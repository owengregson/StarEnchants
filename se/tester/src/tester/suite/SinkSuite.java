package tester.suite;

import compile.model.Affinity;
import engine.sink.DispatchSink;
import java.util.OptionalInt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * Live checks for the affinity-routed {@link DispatchSink} (docs/architecture.md §3.6) — the one
 * thing a unit test cannot prove: that intents actually reach the right thread and mutate the real
 * world, across the cross-region hop that only Folia exercises. The mock-host
 * {@code DispatchSinkTest} pins the routing policy; this pins the routing <em>effect</em>.
 *
 * <ul>
 *   <li>{@code sink.entity.ignite.crossThread} — a {@code TARGET_ENTITY} intent emitted + flushed
 *       from the GLOBAL thread lands on the spawned stand's region thread and sets its fire ticks
 *       (the Folia headline: a genuine cross-region entity hop).</li>
 *   <li>{@code sink.entity.potion.handle} — the §9 round-trip <em>through the Sink</em>: a token is
 *       interned to an id, the dispatcher resolves it back to a live {@code PotionEffectType} on the
 *       entity's thread, and the effect is present on the stand.</li>
 *   <li>{@code sink.region.block.handle} — a {@code REGION} intent resolves a Material handle and
 *       changes a block on the owning region's thread.</li>
 *   <li>{@code sink.inline.zeroHop} — a {@code CONTEXT_LOCAL} intent emitted on the stand's own
 *       thread applies <em>synchronously</em>, with no flush and no scheduler hop.</li>
 * </ul>
 *
 * <p>Every world-touching assertion runs inside {@link Harness#guard} on the correct owning thread,
 * so a wrong-region access (which throws on Folia) is captured as a FAIL, not a silent stall. The
 * spawn chunk is force-loaded on the global thread first — newer servers cull a playerless
 * freshly-spawned entity within a couple of ticks (the behaviour the matrix already surfaced;
 * see {@link SchedulingSuite}).
 */
public final class SinkSuite implements Harness.Scenario {

    private final Plugin plugin;

    public SinkSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("sink.entity.ignite.crossThread");
        h.expect("sink.entity.potion.handle");
        h.expect("sink.region.block.handle");
        h.expect("sink.inline.zeroHop");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        // The resolver interns tokens → ids (compile side); the handles turn those ids back into
        // live objects (runtime side). The SAME resolver backs both, so the round-trip is faithful.
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        // Force-load on the GLOBAL thread (Folia rejects it off the global region), THEN spawn on the
        // location's region, THEN emit/flush from global so the entity hop is a real cross-region hop.
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                ArmorStand victim = spawnStand(world, at);
                ArmorStand inlineStand = spawnStand(world, at.clone().add(0, 0, 1));

                // ── CONTEXT_LOCAL inline: on the stand's OWN thread, applies with zero hop ──
                Scheduling.onEntity(inlineStand, () -> {
                    DispatchSink inlineSink = new DispatchSink(handles);
                    inlineSink.affinity(Affinity.CONTEXT_LOCAL);
                    inlineSink.ignite(inlineStand, 40);
                    h.guard("sink.inline.zeroHop", () -> {
                        if (inlineStand.getFireTicks() != 40) {
                            throw new IllegalStateException(
                                    "CONTEXT_LOCAL ignite did not apply inline; fireTicks="
                                            + inlineStand.getFireTicks());
                        }
                    });
                    inlineStand.remove();
                });

                // ── Deferred intents emitted + flushed from GLOBAL (a different thread than the
                //    victim's region on Folia) — the dispatcher must hop them to the right owner. ──
                Scheduling.onGlobal(() -> {
                    int slowId = resolveId(resolvers.potionEffect("SLOW"), "SLOW");
                    int glowstoneId = resolveId(resolvers.material("GLOWSTONE"), "GLOWSTONE");
                    Location blockAt = at.clone().add(0, 3, 0);

                    DispatchSink sink = new DispatchSink(handles);
                    sink.affinity(Affinity.TARGET_ENTITY);
                    sink.ignite(victim, 80);
                    sink.potion(victim, slowId, 0, 80);
                    sink.affinity(Affinity.REGION);
                    sink.blockChange(blockAt, glowstoneId);
                    sink.flush();

                    // Assert on each owner's thread after a few ticks, once the hops have landed.
                    Scheduling.onEntityLater(victim, 4L, () -> {
                        h.guard("sink.entity.ignite.crossThread", () -> {
                            if (victim.getFireTicks() <= 0) {
                                throw new IllegalStateException(
                                        "cross-thread ignite did not reach the victim; fireTicks="
                                                + victim.getFireTicks());
                            }
                        });
                        h.guard("sink.entity.potion.handle", () -> {
                            PotionEffectType type = handles.potionEffect(slowId);
                            if (type == null) {
                                throw new IllegalStateException("SLOW did not resolve to a live PotionEffectType");
                            }
                            if (!victim.hasPotionEffect(type)) {
                                throw new IllegalStateException("potion handle intent did not apply to the victim");
                            }
                        });
                        victim.remove();
                    });

                    Scheduling.onRegionLater(blockAt, 4L, () -> h.guard("sink.region.block.handle", () -> {
                        Material expected = handles.material(glowstoneId);
                        Material actual = blockAt.getBlock().getType();
                        if (expected == null || actual != expected) {
                            throw new IllegalStateException(
                                    "block change intent did not apply; expected " + expected + " got " + actual);
                        }
                    }));
                });
            });
        });
    }

    private static ArmorStand spawnStand(World world, Location at) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setPersistent(true);
        return stand;
    }

    private static int resolveId(OptionalInt id, String token) {
        if (id.isEmpty()) {
            throw new IllegalStateException(token + " did not intern to a handle id on this version");
        }
        return id.getAsInt();
    }
}
