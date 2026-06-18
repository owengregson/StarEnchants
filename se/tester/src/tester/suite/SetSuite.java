package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import engine.boot.ContentCompiler;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Armour-set resolution, proven live end-to-end (docs/architecture.md §6.6; ADR-0014): a fake player
 * wears armour pieces stamped with a set key, and once the worn-piece count reaches the set's
 * threshold the set's bonus ability joins the player's {@link WornState} (active + in the DEFENSE
 * trigger union) — below the threshold it does not. This exercises the whole set path on a real
 * equipped entity: stamp {@code setKey} into PDC → equip → {@code WornResolver} reads the live
 * armour → {@code SetResolver} → {@code activeSets}. Mojang-mapped only (needs the fake player).
 */
public final class SetSuite implements Harness.Scenario {

    private static final String YETI = """
            display: Yeti
            pieces: 2
            trigger: DEFENSE
            effects: ["POTION:REGENERATION:1:80:@Self"]
            """;

    private final Plugin plugin;

    public SetSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("set.inactiveBelowThreshold");
        h.expect("set.activatesWhenWorn");

        Compiler compiler = ContentCompiler.production();
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        int defenseId = triggers.idOf("DEFENSE").orElseThrow();

        Library library;
        int bonusId;
        try {
            Path root = Files.createTempDirectory("se-set-suite");
            write(root, "sets/yeti.yml", YETI);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("set.activatesWhenWorn", "yeti set failed to compile: " + library.diagnostics());
                return;
            }
            Ability bonus = library.snapshot().byStableKey("sets/yeti");
            if (bonus == null) {
                h.fail("set.activatesWhenWorn", "set bonus ability did not compile");
                return;
            }
            bonusId = bonus.id();
        } catch (IOException e) {
            h.fail("set.activatesWhenWorn", e.toString());
            return;
        }

        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        codec.write(helmet, new CombatState(Map.of(), List.of(), "sets/yeti", false));
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        codec.write(chestplate, new CombatState(Map.of(), List.of(), "sets/yeti", false));

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player wearer;
                try {
                    wearer = FakePlayers.spawn(world, "se_set_wearer");
                } catch (Throwable t) {
                    h.fail("set.activatesWhenWorn", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(wearer, () -> {
                    // One piece — below the 2-piece threshold → the set must be inactive.
                    wearer.getInventory().setHelmet(helmet);
                    wearer.getInventory().setChestplate(null);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.inactiveBelowThreshold", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null || ws.isSetActive(bonusId)) {
                            throw new IllegalStateException("set active with only 1 of 2 pieces");
                        }
                    });

                    // Second piece — threshold met → active + in the DEFENSE union.
                    wearer.getInventory().setChestplate(chestplate);
                    worn.refresh(wearer, library.snapshot());
                    h.guard("set.activatesWhenWorn", () -> {
                        WornState ws = worn.get(wearer.getUniqueId());
                        if (ws == null || !ws.isSetActive(bonusId)) {
                            throw new IllegalStateException("set not active with both pieces worn");
                        }
                        if (!contains(ws.byTrigger(defenseId), bonusId)) {
                            throw new IllegalStateException("active set bonus is not in the DEFENSE trigger union");
                        }
                    });
                    FakePlayers.despawn(wearer);
                });
            });
        });
    }

    private static boolean contains(int[] ids, int id) {
        for (int value : ids) {
            if (value == id) {
                return true;
            }
        }
        return false;
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
