package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.menu.EnchantMenu;
import feature.menu.MenuHolder;
import feature.menu.MenuListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.item.ItemGroups;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Enchant-apply GUI, live (§7): render + click routing + apply through the real {@link MenuListener}.
 * Mojang-mapped only (fake player + a server-side open inventory).
 */
public final class MenuSuite implements Harness.Scenario {

    // Plain display (no own colour code) + an epic tier, so the rendered icon name shows the TIER colour; a
    // multi-line description proves the icon splits it into separate lore lines.
    private static final String KEEN = """
            display: "Keen"
            description:
              - "&7Strikes fast and"
              - "&7cuts deep."
            applies-to: [SWORD]
            trigger: ATTACK
            tier: epic
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
            """;

    private static final String TIERS = """
            default-tier: common
            tiers:
              common: { color: "&7", weight: 10, glint: false }
              epic:   { color: "&e", weight: 40, glint: true  }
            """;

    private final Plugin plugin;

    public MenuSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("menu.clickAppliesEnchant");
        h.expect("menu.iconShowsTierColorAndDescription");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        EnchantMenu menu;
        try {
            Path root = Files.createTempDirectory("se-menu-suite");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "tiers.yml", TIERS);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("menu.clickAppliesEnchant", "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key));
            ItemEnchanter enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard());
            // caps drives the cross-version title cap
            menu = new EnchantMenu(holder, enchanter, player -> { }, Capabilities.probe(plugin.getServer()));
        } catch (IOException e) {
            h.fail("menu.clickAppliesEnchant", e.toString());
            return;
        }

        MenuListener listener = new MenuListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_menu_p");
                } catch (Throwable t) {
                    h.fail("menu.clickAppliesEnchant", "spawn: " + t);
                    HandlerList.unregisterAll(listener);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                    MenuHolder menuHolder = new MenuHolder(menu);
                    menu.render(menuHolder);
                    h.guard("menu.iconShowsTierColorAndDescription", () -> {
                        ItemStack icon = menuHolder.getInventory().getItem(0);
                        if (icon == null) {
                            throw new IllegalStateException("no enchant icon rendered at slot 0");
                        }
                        ItemMeta meta = icon.getItemMeta();
                        String name = meta.getDisplayName();
                        if (!name.startsWith("§e")) { // §e — the epic tier colour, applied to the name
                            throw new IllegalStateException("icon name should carry the epic tier colour (§e); got: " + name);
                        }
                        java.util.List<String> iconLore = meta.getLore();
                        long descLines = iconLore == null ? 0 : iconLore.stream()
                                .filter(l -> l.contains("Strikes fast") || l.contains("cuts deep")).count();
                        if (descLines != 2) { // both description lines, each as its OWN lore entry (newlines split)
                            throw new IllegalStateException(
                                    "the two description lines should render as separate lore entries; lore=" + iconLore);
                        }
                    });
                    InventoryView view = player.openInventory(menuHolder.getInventory());
                    InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                            0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    plugin.getServer().getPluginManager().callEvent(click);
                    h.guard("menu.clickAppliesEnchant", () -> {
                        if (!click.isCancelled()) {
                            throw new IllegalStateException("menu click was not cancelled — items could be moved");
                        }
                        CombatState state = codec.read(player.getInventory().getItemInMainHand());
                        if (!state.enchants().containsKey("enchants/keen")) {
                            throw new IllegalStateException("held item did not gain enchants/keen after the click; "
                                    + "enchants=" + state.enchants());
                        }
                    });
                    player.closeInventory();
                    HandlerList.unregisterAll(listener);
                    FakePlayers.despawn(player);
                });
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
