package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.sink.SwarmClouds;
import engine.sink.SwarmRing;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.pet.PetArmedStore;
import feature.pet.PetWornSource;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.PetCodec;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import tester.harness.CombatRig;
import tester.harness.CrossRegion;
import tester.harness.Harness;

/**
 * The ADR-0068 attacker vision cloud, live: a REAL pre-summon hit seeds the most-recent attacker at
 * arm time (the only acquisition path — noteHit is a no-op before the summon arms the cloud), the
 * owner-side publisher tracks the facing pillar as the attacker turns, the swarm converges on the
 * orbit, an out-of-range attacker reverts the swarm to vanilla AI WITHOUT despawning it, and a
 * genuinely cross-region attacker fails CLOSED (target nulls, live bats, no server fault). Staged on
 * an elevated arena away from world spawn: PetAbilitySuite's concurrent bat census (and any wild
 * cave bat) must never intersect this suite's 12 long-TTL summons. Clientless fake players;
 * server-side positions only.
 */
public final class BatCloudSuite implements Harness.Scenario {

    private static final String BAT = """
            display: Bat
            type: ACTIVE
            levels:
              1: { cooldown: 0, effects: [ { SPAWN_SWARM: { type: BAT, count: 12, radius: 0.5, ttl: 400, speed: 0.5, cloud: true, cloud-range: 16 } } ] }
            """;

    private static final String TRACKS = "batcloud.pillarTracksAttackerFacing";
    private static final String CONVERGE = "batcloud.batsConvergeOnThePillar";
    private static final String RANGE_DROP = "batcloud.dropsWhenTheAttackerLeavesRange";
    private static final String CROSS_REGION = "batcloud.crossRegionAttackerFailsClosed";
    private static final List<String> CHECKS = List.of(TRACKS, CONVERGE, RANGE_DROP, CROSS_REGION);

    /** Fake players carry ~60 ticks of spawn invulnerability — the staged seed hit waits it out. */
    private static final long SPAWN_INVULN_TICKS = 70L;
    /** Sky arena: outside every world-spawn suite's scan box and above any cave-spawned wild bat. */
    private static final int ARENA_OFFSET = 96;
    private static final int ARENA_Y = 200;
    private static final double PILLAR_EPS = 0.05;

    private final Plugin plugin;

    public BatCloudSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        CHECKS.forEach(h::expect);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-batcloud-suite");
            Path file = root.resolve("pets/bat.yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, BAT, StandardCharsets.UTF_8);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "bat failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        PetCodec petCodec = new PetCodec(keys, Stores.state());
        CombatCodec combat = new CombatCodec(keys.combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(combat, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        AtomicLong tick = new AtomicLong();
        PetArmedStore armed = new PetArmedStore();
        PetWornSource petSource = new PetWornSource(() -> true, petCodec, holder::library, armed, tick::get);
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews,
                triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers(),
                () -> WornResolver.Features.ALL, Set::of, petSource)::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        // ONE env for BOTH dispatches (load-bearing): the seed hit lands BEFORE the summon arms the
        // cloud, so CombatDispatch's RecentAttackersStore write and the sink's arm-time seed read must
        // hit the SAME store. nowTicks is tick::get and the repeatingGlobal task below is the ONLY
        // clock advancer — an incrementing read would double-advance time and stale the windows.
        SinkEnv env = SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(), tick::get);
        TriggerDispatch useDispatch = new TriggerDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());
        CombatDispatch combatDispatch = new CombatDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());

        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(combatDispatch));

        // The cloud windows/staleness are tick-budget — a frozen clock would instantly stale every target.
        TaskHandle[] clock = new TaskHandle[1];
        clock[0] = Scheduling.repeatingGlobal(1L, 1L, tick::incrementAndGet);

        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        Location arena = new Location(world, spawn.getX() + ARENA_OFFSET, ARENA_Y, spawn.getZ() + ARENA_OFFSET);
        Location attackerAt = new Location(world, arena.getX() + 3, arena.getY(), arena.getZ(), 0.0f, 0.0f);
        Location rangeDropAt = arena.clone().add(40, 0, 0);
        Location farRegion = arena.clone().add(CrossRegion.GAP, 0, CrossRegion.GAP);

        // The rig force-loads spawn + farRegion; the arena and range-drop chunks are ours to pin.
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(arena.getBlockX() >> 4, arena.getBlockZ() >> 4, true);
            world.setChunkForceLoaded(rangeDropAt.getBlockX() >> 4, rangeDropAt.getBlockZ() >> 4, true);
        });

        rig.onArena(spawn, farRegion, () -> {
            Player owner;
            Player attacker;
            try {
                owner = rig.spawnFake(world, "se_batcloud_o");
                attacker = rig.spawnFake(world, "se_batcloud_a");
            } catch (Throwable t) {
                abort(h, rig, clock, "fake-player spawn: " + t);
                return;
            }
            hop(owner, arena, () -> hop(attacker, attackerAt, () ->
                    Scheduling.onEntityLater(owner, SPAWN_INVULN_TICKS, () ->
                            stage(h, rig, clock, tick, useDispatch, holder, owner, attacker,
                                    attackerAt, rangeDropAt, farRegion))));
        });
    }

    /** On the owner's thread, past the i-frame window: the REAL seed hit, the summon, then the checks. */
    private void stage(Harness h, CombatRig rig, TaskHandle[] clock, AtomicLong tick,
                       TriggerDispatch useDispatch, ContentHolder holder, Player owner, Player attacker,
                       Location attackerAt, Location rangeDropAt, Location farRegion) {
        UUID ownerId = owner.getUniqueId();
        double ax = attackerAt.getX();
        double ay = attackerAt.getY();
        double az = attackerAt.getZ();
        try {
            // The pre-summon hit: recorded into RecentAttackersStore; SwarmClouds.noteHit is still a
            // no-op here (nothing armed), so the arm-time seed is the ONLY acquisition path under test.
            owner.damage(2.0, attacker);
        } catch (Throwable t) {
            abort(h, rig, clock, "the staged seed hit threw: " + t);
            return;
        }
        useDispatch.fireUse(owner, holder.library().petDefOf("bat").bracketFor(1).useStableKeys());
        awaitUntil(owner, () -> SwarmClouds.target(ownerId, tick.get()) != null, 0, 20, acquired -> {
            if (!acquired) {
                abort(h, rig, clock, "no cloud target within 20 ticks of the summon (seed did not resolve)");
                return;
            }
            // Hand-derived expectations (never recomputed via SwarmRing — that would mask a wrong
            // facing formula): yaw 0 faces +Z, so the pillar sits 1 block +Z of the attacker's feet.
            SwarmClouds.CloudTarget front = SwarmClouds.target(ownerId, tick.get());
            String yaw0Fault = front == null
                    ? "the target went stale between polls"
                    : pillarFault("yaw-0", front, ax, az + 1.0, ay);
            Location turned = attackerAt.clone();
            turned.setYaw(90.0f);
            hop(attacker, turned, () ->
                    awaitUntil(owner, () -> nearPillar(SwarmClouds.target(ownerId, tick.get()), ax - 1.0, az),
                            0, 20, swung -> {
                                h.guard(TRACKS, () -> {
                                    if (yaw0Fault != null) {
                                        throw new IllegalStateException(yaw0Fault);
                                    }
                                    if (!swung) {
                                        throw new IllegalStateException(
                                                "the pillar did not follow the yaw-90 turn to (" + (ax - 1.0)
                                                        + ", " + az + "); last="
                                                        + SwarmClouds.target(ownerId, tick.get()));
                                    }
                                });
                                stageConverge(h, rig, clock, tick, owner, ownerId, ay, attacker,
                                        rangeDropAt, farRegion);
                            }));
        });
    }

    private void stageConverge(Harness h, CombatRig rig, TaskHandle[] clock, AtomicLong tick, Player owner,
                               UUID ownerId, double attackerFeetY, Player attacker,
                               Location rangeDropAt, Location farRegion) {
        Scheduling.onEntityLater(owner, 40L, () -> {
            h.guard(CONVERGE, () -> {
                SwarmClouds.CloudTarget t = SwarmClouds.target(ownerId, tick.get());
                if (t == null) {
                    throw new IllegalStateException("the cloud target vanished before the convergence scan");
                }
                int bats = 0;
                for (Entity near : owner.getNearbyEntities(30, 30, 30)) {
                    if (near.getType() == EntityType.BAT) {
                        bats++;
                        Location at = near.getLocation();
                        double dh = Math.hypot(at.getX() - t.x(), at.getZ() - t.z());
                        if (dh > SwarmRing.CLOUD_ORBIT_RADIUS + 1.5) {
                            throw new IllegalStateException("a bat strayed " + dh + " blocks from the pillar");
                        }
                        double dy = at.getY() - attackerFeetY;
                        if (dy < -0.3 || dy > 2.4) {
                            throw new IllegalStateException("a bat left the pillar column: dy=" + dy);
                        }
                    }
                }
                if (bats != 12) {
                    throw new IllegalStateException("expected 12 clouding bats, found " + bats);
                }
            });
            // Out of "nearby": 40 blocks is past the release gate; the revert must NOT despawn the swarm.
            hop(attacker, rangeDropAt, () ->
                    awaitUntil(owner, () -> SwarmClouds.target(ownerId, tick.get()) == null, 0, 10, dropped -> {
                        h.guard(RANGE_DROP, () -> {
                            if (!dropped) {
                                throw new IllegalStateException("the target survived an attacker 40 blocks out");
                            }
                            int alive = countBats(owner);
                            if (alive != 12) {
                                throw new IllegalStateException("the revert despawned bats: " + alive + "/12 alive");
                            }
                        });
                        // A genuinely distinct Folia region (Paper degenerates to a far teleport — same
                        // assertion): the guarded read fails CLOSED — null target, live bats, no fault.
                        hop(attacker, farRegion, () ->
                                awaitUntil(owner, () -> SwarmClouds.target(ownerId, tick.get()) == null,
                                        0, SwarmClouds.STALE_TICKS + 10, closed -> {
                                            h.guard(CROSS_REGION, () -> {
                                                if (!closed) {
                                                    throw new IllegalStateException(
                                                            "a cross-region attacker kept a live target");
                                                }
                                                int alive = countBats(owner);
                                                if (alive != 12) {
                                                    throw new IllegalStateException(
                                                            "cross-region fail-closed lost bats: " + alive
                                                                    + "/12 alive");
                                                }
                                            });
                                            if (clock[0] != null) {
                                                clock[0].cancel();
                                            }
                                            rig.teardown();
                                        }));
                    }));
        });
    }

    /** Non-null = why {@code t} is not the expected pillar (±{@value PILLAR_EPS} per axis). */
    private static String pillarFault(String what, SwarmClouds.CloudTarget t, double x, double z, double y) {
        if (Math.abs(t.x() - x) > PILLAR_EPS || Math.abs(t.z() - z) > PILLAR_EPS
                || Math.abs(t.y() - y) > PILLAR_EPS) {
            return what + " pillar expected (" + x + ", " + y + ", " + z + "), got ("
                    + t.x() + ", " + t.y() + ", " + t.z() + ")";
        }
        return null;
    }

    private static boolean nearPillar(SwarmClouds.CloudTarget t, double x, double z) {
        return t != null && Math.abs(t.x() - x) <= PILLAR_EPS && Math.abs(t.z() - z) <= PILLAR_EPS;
    }

    /** BAT census around {@code owner} — call on the owner's thread ({@link #awaitUntil} delivers there). */
    private static int countBats(Player owner) {
        int bats = 0;
        for (Entity near : owner.getNearbyEntities(30, 30, 30)) {
            if (near.getType() == EntityType.BAT) {
                bats++;
            }
        }
        return bats;
    }

    /**
     * Folia-safe staging hop (the PetHomeSuite idiom): teleportAsync from the player's own scheduler —
     * a synchronous teleport throws under region threading — continuing once landed plus a settle tick.
     */
    private static void hop(Player user, Location to, Runnable then) {
        Scheduling.onEntity(user, () ->
                user.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(user, 2L, then)));
    }

    /**
     * Poll each game tick until {@code cond} holds or {@code maxTicks} elapse. {@code done} is ALWAYS
     * delivered on {@code entity}'s scheduler: the first poll runs on the caller's thread, which after a
     * cross-region hop is the WRONG side for the Bukkit reads the continuations do.
     */
    private static void awaitUntil(Entity entity, BooleanSupplier cond, int tick, int maxTicks,
                                   Consumer<Boolean> done) {
        if (cond.getAsBoolean()) {
            Scheduling.onEntity(entity, () -> done.accept(true));
            return;
        }
        if (tick >= maxTicks) {
            Scheduling.onEntity(entity, () -> done.accept(false));
            return;
        }
        Scheduling.onEntityLater(entity, 1L, () -> awaitUntil(entity, cond, tick + 1, maxTicks, done));
    }

    /** Stage failure before any check resolved: fail everything, stop the clock, unwind the rig. */
    private void abort(Harness h, CombatRig rig, TaskHandle[] clock, String reason) {
        failAll(h, reason);
        if (clock[0] != null) {
            clock[0].cancel();
        }
        rig.teardown();
    }

    private void failAll(Harness h, String message) {
        for (String check : CHECKS) {
            h.fail(check, message);
        }
    }
}
