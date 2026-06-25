package tester.suite;

import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierService;
import feature.menu.AlchemistMenu;
import feature.menu.MenuHolder;
import feature.menu.MenuListener;
import feature.menu.TinkererMenu;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * §K interactive merchant benches with cross-version/Folia risk beyond unit coverage: input-slot placement
 * vs filler lock, combine/salvage mutations, close-returns-staged-inputs — run through the real
 * {@link MenuListener}. (Display-menu click→apply lives in {@link MenuSuite}; pure logic in unit tests.)
 */
public final class GuiSuite implements Harness.Scenario {

    /** Two levels, so a level-1 + level-1 combine has a level-2 to produce. */
    private static final String KEEN = """
            display: "&bKeen"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:1"] }
              2: { chance: 100, effects: ["MODIFY_HEALTH:2"] }
            """;

    private static final String KEY = "enchants/keen";
    private static final int ALCHEMIST_LEFT = 11;
    private static final int ALCHEMIST_COMBINE = 13;
    private static final int ALCHEMIST_RIGHT = 15;
    private static final int TINKERER_INPUT = 13;
    private static final int TINKERER_SALVAGE = 15;

    private final Plugin plugin;

    public GuiSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("gui.inputSlotPlacementVsLock");
        h.expect("gui.alchemistCombines");
        h.expect("gui.tinkererSalvages");
        h.expect("gui.closeReturnsInputs");

        CarrierService carriers;
        AlchemistMenu alchemist;
        TinkererMenu tinkerer;
        MenuListener listener;
        try {
            Path root = Files.createTempDirectory("se-gui-suite");
            write(root, "enchants/keen.yml", KEEN);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                failAll(h, "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder content = new ContentHolder(library);
            ItemKeys keys = ItemKeys.of(plugin);
            CombatCodec combat = new CombatCodec(keys.combat());
            LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> content.library().displayNameOf(key));
            ItemEnchanter enchanter = new ItemEnchanter(combat, lore, content, ItemGroups.standard());
            carriers = new CarrierService(new CarrierCodec(keys.carrier(), keys.guarded()), enchanter, content,
                    new Random(1));
            Capabilities caps = Capabilities.probe(plugin.getServer());
            alchemist = new AlchemistMenu(carriers, caps);
            tinkerer = new TinkererMenu(carriers, caps);
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        listener = new MenuListener();
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
                    player = FakePlayers.spawn(world, "se_gui_p");
                } catch (Throwable t) {
                    failAll(h, "spawn: " + t);
                    HandlerList.unregisterAll(listener);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    try {
                        runChecks(h, player, carriers, alchemist, tinkerer);
                    } finally {
                        player.closeInventory();
                        HandlerList.unregisterAll(listener);
                        FakePlayers.despawn(player);
                    }
                });
            });
        });
    }

    private void runChecks(Harness h, Player player, CarrierService carriers,
                           AlchemistMenu alchemist, TinkererMenu tinkerer) {
        h.guard("gui.inputSlotPlacementVsLock", () -> {
            InventoryView view = open(player, alchemist);
            if (dispatchClick(view, ALCHEMIST_LEFT).isCancelled()) {
                throw new IllegalStateException("an input slot click was cancelled — items could not be placed");
            }
            if (!dispatchClick(view, 0).isCancelled()) {
                throw new IllegalStateException("a filler slot click was NOT cancelled — the bench is not locked");
            }
        });

        h.guard("gui.alchemistCombines", () -> {
            InventoryView view = open(player, alchemist);
            player.getInventory().clear();
            view.getTopInventory().setItem(ALCHEMIST_LEFT, carriers.mintBook(KEY, 1));
            view.getTopInventory().setItem(ALCHEMIST_RIGHT, carriers.mintBook(KEY, 1));
            dispatchClick(view, ALCHEMIST_COMBINE);
            if (view.getTopInventory().getItem(ALCHEMIST_LEFT) != null
                    || view.getTopInventory().getItem(ALCHEMIST_RIGHT) != null) {
                throw new IllegalStateException("combine did not consume both input books");
            }
            if (!hasBookAtLevel(player, carriers, 2)) {
                throw new IllegalStateException("player did not receive a level-2 book after combining");
            }
        });

        h.guard("gui.tinkererSalvages", () -> {
            InventoryView view = open(player, tinkerer);
            player.getInventory().clear();
            player.setLevel(0);
            view.getTopInventory().setItem(TINKERER_INPUT, carriers.mintBook(KEY, 3));
            dispatchClick(view, TINKERER_SALVAGE);
            if (view.getTopInventory().getItem(TINKERER_INPUT) != null) {
                throw new IllegalStateException("salvage did not consume the book");
            }
            if (player.getLevel() != 3) {
                throw new IllegalStateException("salvage refunded " + player.getLevel() + " levels, expected 3");
            }
        });

        h.guard("gui.closeReturnsInputs", () -> {
            InventoryView view = open(player, alchemist);
            player.getInventory().clear();
            view.getTopInventory().setItem(ALCHEMIST_LEFT, carriers.mintBook(KEY, 1));
            plugin.getServer().getPluginManager().callEvent(new InventoryCloseEvent(view));
            if (view.getTopInventory().getItem(ALCHEMIST_LEFT) != null) {
                throw new IllegalStateException("the staged input was not cleared on close");
            }
            if (!hasBookAtLevel(player, carriers, 1)) {
                throw new IllegalStateException("the staged book was not returned to the player on close");
            }
        });
    }

    private static InventoryView open(Player player, feature.menu.Menu menu) {
        MenuHolder holder = new MenuHolder(menu);
        menu.render(holder);
        return player.openInventory(holder.getInventory());
    }

    private InventoryClickEvent dispatchClick(InventoryView view, int rawSlot) {
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        plugin.getServer().getPluginManager().callEvent(click);
        return click;
    }

    private static boolean hasBookAtLevel(Player player, CarrierService carriers, int level) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && carriers.bookContents(stack).filter(b -> b.level() == level).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void failAll(Harness h, String reason) {
        h.fail("gui.inputSlotPlacementVsLock", reason);
        h.fail("gui.alchemistCombines", reason);
        h.fail("gui.tinkererSalvages", reason);
        h.fail("gui.closeReturnsInputs", reason);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
