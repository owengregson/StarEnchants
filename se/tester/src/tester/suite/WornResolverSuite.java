package tester.suite;

import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Snapshot;
import engine.boot.ContentCompiler;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * Item → worn-state path, live (ADR-0014, §5.5): write an enchant onto an item → equip → {@link WornResolver}
 * resolves the path-derived key against the snapshot and flattens it into a {@link WornState}. Uses an armour
 * stand (wears equipment on every version), so this runs the spigot-mapped floor too — not just the mojang fake player.
 */
public final class WornResolverSuite implements Harness.Scenario {

    private static final String LIFESTEAL = """
            display: Lifesteal
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MODIFY_HEALTH:2"] }
              2: { chance: 100, effects: ["MODIFY_HEALTH:4"] }
              3: { chance: 100, effects: ["MODIFY_HEALTH:6"] }
            """;

    private final Plugin plugin;

    public WornResolverSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("worn.resolveFromArmor");

        // MODIFY_HEALTH is handle-free, so loading on the global thread resolves nothing version-volatile.
        Snapshot snapshot;
        int expectedId;
        try {
            Path root = Files.createTempDirectory("se-worn-suite");
            write(root, "enchants/lifesteal.yml", LIFESTEAL);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            snapshot = library.snapshot();
            expectedId = snapshot.stableKeys().idOf("enchants/lifesteal/3");
        } catch (IOException e) {
            h.fail("worn.resolveFromArmor", e.toString());
            return;
        }
        if (expectedId < 0) {
            h.fail("worn.resolveFromArmor", "enchants/lifesteal/3 missing from the snapshot");
            return;
        }

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, 0);
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornResolver resolver = new WornResolver(itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers());

        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        codec.write(helmet, new CombatState(Map.of("enchants/lifesteal", 3), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        final Snapshot snap = snapshot;
        final int wantId = expectedId;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
                stand.setInvisible(true);
                stand.setGravity(false);
                stand.setPersistent(true);
                Scheduling.onEntity(stand, () -> {
                    h.guard("worn.resolveFromArmor", () -> {
                        stand.getEquipment().setHelmet(helmet);
                        WornState worn = resolver.resolve(stand, snap);
                        if (worn.gen() != snap.generation()) {
                            throw new IllegalStateException("worn-state generation " + worn.gen()
                                    + " != snapshot " + snap.generation());
                        }
                        if (!contains(worn.combatAttack(), wantId)) {
                            throw new IllegalStateException("lifesteal ability id " + wantId
                                    + " not in combatAttack " + Arrays.toString(worn.combatAttack()));
                        }
                    });
                    stand.remove();
                    Scheduling.onGlobal(() -> world.setChunkForceLoaded(cx, cz, false));
                });
            });
        });
    }

    private static boolean contains(int[] ids, int target) {
        for (int id : ids) {
            if (id == target) {
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
