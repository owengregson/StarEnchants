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

    private static final String KEEN = """
            display: "&bKeen"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
            """;

    private final Plugin plugin;

    public MenuSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("menu.clickAppliesEnchant");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        EnchantMenu menu;
        try {
            Path root = Files.createTempDirectory("se-menu-suite");
            write(root, "enchants/keen.yml", KEEN);
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
