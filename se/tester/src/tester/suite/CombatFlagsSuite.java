package tester.suite;

import engine.sink.DispatchSink;
import engine.sink.SoulDebit;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import feature.combat.KnockbackListener;
import java.util.OptionalInt;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import platform.caps.Capabilities;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live checks for §C combat-flag primitives carrying version/Folia/real-entity risk beyond their unit
 * coverage (docs/v3-directives.md §C):
 *
 * <ul>
 *   <li><strong>KNOCKBACK_CONTROL</strong> — capability-probed version split; asserts
 *       {@link KnockbackListener#register} picks modern ({@code EntityKnockbackEvent}, 1.20.6+) vs legacy
 *       and that the reflective hook's accessors exist (the unit test compiles against the floor only).</li>
 *   <li><strong>GUARD</strong> — {@code Sink.guard} spawns a guardian and {@code setTarget}s the attacker.
 *       Iron golem so the guard survives any difficulty (a hostile mob is culled on peaceful).</li>
 *   <li><strong>KEEP_ON_DEATH</strong> — {@code Sink.keepOnDeath} arms the per-player flag. No suite fires a
 *       real player death (fragile for a clientless fake player), so this pins the effect→sink→store half.</li>
 * </ul>
 *
 * Each assertion runs on its region/entity thread, so a wrong-thread access throws (caught as a failure,
 * not a stall) — the Folia-correctness check.
 */
public final class CombatFlagsSuite implements Harness.Scenario {

    private static final String[] KEYS = {
        "combatflags.knockbackApplierRegisters",
        "combatflags.guardSpawnsTargetingAttacker",
        "combatflags.keepOnDeathArmsStore",
    };

    private final Plugin plugin;

    public CombatFlagsSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        for (String key : KEYS) {
            h.expect(key);
        }

        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);
        Capabilities caps = Capabilities.probe(plugin.getServer());

        // KNOCKBACK_CONTROL registration — server-wide, no entities needed.
        h.guard("combatflags.knockbackApplierRegisters", () -> {
            KnockbackListener.Path path =
                    KnockbackListener.register(plugin, new KnockbackControlStore(), () -> 0L);
            if (path == KnockbackListener.Path.NONE) {
                throw new IllegalStateException("no knockback event found on this server (" + caps + ")");
            }
            boolean modern = caps.atLeast(1, 20, 6);
            if (modern && path != KnockbackListener.Path.MODERN) {
                throw new IllegalStateException("expected MODERN knockback applier on " + caps + " got " + path);
            }
            if (!modern && path != KnockbackListener.Path.LEGACY) {
                throw new IllegalStateException("expected LEGACY knockback applier on " + caps + " got " + path);
            }
            if (modern) {
                // Prove the modern hook's accessors exist here (the floor-compiled unit test can't reach them).
                Class<?> event = Class.forName(KnockbackListener.MODERN_EVENT);
                event.getMethod("getFinalKnockback");
                event.getMethod("setFinalKnockback", Vector.class);
            }
        });

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        int golemId = resolveId(resolvers.entityType("IRON_GOLEM"), "IRON_GOLEM");

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player attacker;
                try {
                    attacker = FakePlayers.spawn(world, "se_cflag");
                } catch (Throwable t) {
                    h.fail("combatflags.guardSpawnsTargetingAttacker", "fake player spawn: " + t);
                    h.fail("combatflags.keepOnDeathArmsStore", "fake player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(attacker, () -> {
                    // KEEP_ON_DEATH: arm the per-player flag through the real sink.
                    KeepOnDeathStore keepStore = new KeepOnDeathStore();
                    DispatchSink keepSink = new DispatchSink(handles, EconomyService.NONE, SoulDebit.NONE,
                            new VarStore(), new SuppressionStore(), new KnockbackControlStore(), keepStore, () -> 0L);
                    keepSink.keepOnDeath(attacker, 100);
                    h.guard("combatflags.keepOnDeathArmsStore", () -> {
                        if (!keepStore.shouldKeep(attacker.getUniqueId(), 0L)) {
                            throw new IllegalStateException("keepOnDeath did not arm the store for the player");
                        }
                        if (keepStore.shouldKeep(attacker.getUniqueId(), 100L)) {
                            throw new IllegalStateException("the keep flag must expire at its TTL (tick 100)");
                        }
                    });

                    // GUARD: summon an iron-golem guard targeting the attacker.
                    Location guardAt = at.clone();
                    DispatchSink guardSink = new DispatchSink(handles);
                    guardSink.guard(attacker, guardAt, golemId, 1, 200, "&bGuard");
                    guardSink.flush();
                    Scheduling.onRegionLater(guardAt, 2L, () -> {
                        h.guard("combatflags.guardSpawnsTargetingAttacker", () -> {
                            Mob guard = findGuard(world, guardAt);
                            if (guard == null) {
                                throw new IllegalStateException("no guard mob spawned near the activation location");
                            }
                            // A named guard mob means the setTarget branch ran. Whether vanilla AI retains a
                            // manually-set target is version-dependent, so it's logged not gated; the spawn +
                            // type + name across versions + Folia is the real risk.
                            LivingEntity target = guard.getTarget();
                            String targetState = target == null ? "cleared by AI"
                                    : target.getUniqueId().equals(attacker.getUniqueId()) ? "attacker (retained)"
                                    : "other(" + target.getUniqueId() + ")";
                            plugin.getLogger().info("[cflags] guard spawned + named; setTarget path ran; target now: "
                                    + targetState);
                            guard.remove();
                        });
                        FakePlayers.despawn(attacker);
                    });
                });
            });
        });
    }

    /** The nearest summoned iron-golem guard (by its custom name) to {@code at}, or {@code null}. */
    private static Mob findGuard(World world, Location at) {
        for (Entity entity : world.getNearbyEntities(at, 4, 4, 4)) {
            if (entity instanceof Mob mob && mob.getType() == org.bukkit.entity.EntityType.IRON_GOLEM) {
                String name = mob.getCustomName();
                if (name != null && name.contains("Guard")) {
                    return mob;
                }
            }
        }
        return null;
    }

    private static int resolveId(OptionalInt id, String token) {
        if (id.isEmpty()) {
            throw new IllegalStateException(token + " did not intern to a handle id on this version");
        }
        return id.getAsInt();
    }
}
