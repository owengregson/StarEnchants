package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.menu.EnchantMenu;
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
import platform.item.ItemGroups;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * The enchant-application GUI, live (docs/architecture.md §7): a fake player opens the {@link EnchantMenu},
 * a click on an enchant icon is dispatched through the real {@link MenuListener}, and the enchant lands
 * on the player's held item — proving menu render + click routing + apply end-to-end on Paper and Folia.
 * Mojang-mapped only (needs the fake player + a server-side open inventory). The click is simulated by
 * firing a real {@code InventoryClickEvent} through the plugin manager, so the registered listener handles
 * it exactly as a player click would.
 */
public final class MenuSuite implements Harness.Scenario {

    private static final String KEEN = """
            display: "&bKeen"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """;

    private final Plugin plugin;

    public MenuSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("menu.clickAppliesEnchant");

        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
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
            menu = new EnchantMenu(holder, enchanter, player -> { }); // no WornState store needed for this check
        } catch (IOException e) {
            h.fail("menu.clickAppliesEnchant", e.toString());
            return;
        }

        MenuListener listener = new MenuListener(menu);
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
                    // Open page 0 server-side and dispatch a real click on slot 0 (the only enchant, "keen").
                    InventoryView view = player.openInventory(menu.build(0));
                    InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                            0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    plugin.getServer().getPluginManager().callEvent(click); // handled inline by MenuListener
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
