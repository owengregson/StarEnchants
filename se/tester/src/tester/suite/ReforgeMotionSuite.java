package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Lang;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.ReforgeDef;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.boot.ContentCompiler;
import engine.effect.kind.BlinkEffect;
import engine.effect.kind.BuiltinEffects;
import engine.effect.kind.GravityWellEffect;
import engine.effect.kind.GrappleEffect;
import engine.effect.kind.JavelinEffect;
import engine.effect.kind.SwapPositionEffect;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.run.UseAttempt;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.reforge.CastlingService;
import feature.reforge.GravityWellService;
import feature.reforge.GrappleService;
import feature.reforge.JavelinService;
import feature.reforge.WeaponDamage;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.economy.EconomyService;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.CrossRegion;
import tester.harness.Harness;

/**
 * The five ADR-0071 movement/space reforge surfaces, live — the seams only a booted server proves. BLINK is a
 * real engine kind driven through {@code fireUse} against the true block walk; GRAPPLE/GRAVITY_WELL/SWAP_POSITION
 * (Castling)/JAVELIN are service-owned markers whose machines this suite arms directly with the compiled effect
 * (the reforge runner's activation-success hook), reading the CURRENT snapshot exactly like {@code ReforgeMachines}.
 * The raycast is the injected composition-only seam ({@code targetBlock}/{@code targetEntity} method refs), so each
 * scenario stubs it to a staged block/entity — the mechanics (blink walk, reel/zip, pull/implosion, channel swap,
 * flight/knockback/hold) then run against real staged entities and blocks, which is the part the unit tests cannot
 * reach.
 *
 * <p>Every scenario runs on its own {@link CombatRig} (isolated listeners + teardown) at its own elevated void
 * arena, spaced far past every effect radius so concurrently-launched scenarios never intersect. Victims are cows
 * (the peaceful matrix deletes hostiles) or clientless fake players; both are pinned with gravity off for
 * deterministic positions, and fake-player victims wait out the ~60-tick spawn invulnerability
 * ({@link #SPAWN_INVULN_TICKS}) before any staged hit and have {@code noDamageTicks} zeroed. Waits are tick-anchored;
 * positions are read server-side on the entity's own thread.
 *
 * <p>Modern-build suite (the tester tree compiles against modern Paper; the 1.8.9 lane's own compile gate covers
 * the era leaves). The cross-region attribution-degrade check is meaningful only under Folia region threading and
 * PASSes trivially on single-threaded Paper.
 */
public final class ReforgeMotionSuite implements Harness.Scenario {

    /** Fake players carry ~60 ticks of spawn invulnerability — a staged hit inside it silently no-ops. */
    private static final long SPAWN_INVULN_TICKS = 70L;
    /** Elevated void arena, above any terrain/cave spawn and every other suite's scan box. */
    private static final int ARENA_Y = 200;
    /** Distance from world spawn to the first arena — clear of the ground-level combat suites. */
    private static final int ARENA_BASE = 512;
    /** Between-scenario spacing — far past grapple range (14) / javelin travel (12) / well radius (6). */
    private static final int ARENA_STEP = 96;
    /** The Singularity {@code rise}: core sits {@code 1 + rise} blocks above the sighted block (matches the def below). */
    private static final double SINGULARITY_RISE = 2.5;
    private static final double POS_EPS = 0.40;
    private static final double HEALTH_EPS = 0.75;

    // Arena indices — each check owns a distinct sky arena so the machines never cross-contaminate.
    private static final int A_BLINK_WALL = 0;
    private static final int A_BLINK_ZERO = 1;
    private static final int A_BLINK_PHASE = 2;
    private static final int A_GRAPPLE_REEL = 3;
    private static final int A_GRAPPLE_ZIP = 4;
    private static final int A_GRAPPLE_WALL = 5;
    private static final int A_SING_BEAM = 6;
    private static final int A_SING_PULL = 7;
    private static final int A_SING_IMPLODE = 8;
    private static final int A_SING_SELF = 9;
    private static final int A_CAST_SWAP = 10;
    private static final int A_CAST_LOS = 11;
    private static final int A_CAST_CUES = 12;
    private static final int A_CAST_CD = 13;
    private static final int A_JAV_MISS = 14;
    private static final int A_JAV_HIT = 15;
    private static final int A_JAV_KB = 16;
    private static final int A_JAV_HOLD = 17;
    private static final int A_JAV_WALL = 18;
    private static final int A_XREGION = 19;
    private static final int A_BLINK_PITCH = 20;

    // ── Test defs (own numbers, LibraryLoader-compiled like BatCloudSuite; keys are reforges/<stem>) ──

    private static final String SINGULARITY = """
            display: Singularity
            color: "&d"
            icon: ENDER_EYE
            description: [ "&etest" ]
            abilities:
              - cooldown: 1200
                effects:
                  - { GRAVITY_WELL: { range: 12, radius: 6, rise: 2.5, duration: 40, period: 2, pull: 0.4, damage: 8.0, falloff-floor: 0.25, self-pull: true, self-damage: true, r: 190, g: 120, b: 255 } }
            """;

    /** No pull + a short window: the cows hold their staged distances so the implosion falloff is clean. */
    private static final String SINGULARITY_QUICK = """
            display: Singularity
            color: "&d"
            icon: ENDER_EYE
            description: [ "&etest" ]
            abilities:
              - cooldown: 1200
                effects:
                  - { GRAVITY_WELL: { range: 12, radius: 6, rise: 2.5, duration: 8, period: 2, pull: 0.0, damage: 8.0, falloff-floor: 0.25, self-pull: false, self-damage: false, r: 190, g: 120, b: 255 } }
            """;

    private static final String LEVIATHAN = """
            display: Leviathan's Reach
            color: "&6"
            icon: FISHING_ROD
            description: [ "&etest" ]
            abilities:
              - cooldown: 400
                effects:
                  - { GRAPPLE: { range: 14, hook-speed: 2.0, reel-distance: 2.0, slow-effect: "SLOW", slow-level: 2, slow-duration: 60, zip-strength: 0.34, zip-cap: 3.2, zip-rise: 0.25, particle: REDSTONE, r: 200, g: 220, b: 255, size: 1.0, density: 3 } }
            """;

    private static final String BLINK = """
            display: Blink
            color: "&e"
            icon: CHORUS_FRUIT
            description: [ "&etest" ]
            abilities:
              - cooldown: 200
                effects:
                  - { BLINK: { distance: 4, particle: REDSTONE, r: 170, g: 60, b: 220, size: 1.0, count: 10 } }
            """;

    private static final String CASTLING = """
            display: Castling
            color: "&6"
            icon: STONE_BRICKS
            description: [ "&etest" ]
            abilities:
              - cooldown: 600
                effects:
                  - { SWAP_POSITION: { range: 12, channel: 40, check-period: 2, cue-period: 10, range-slack: 2 } }
            """;

    private static final String JAVELIN = """
            display: Javelin
            color: "&e"
            icon: TRIDENT
            description: [ "&etest" ]
            abilities:
              - cooldown: 600
                effects:
                  - { JAVELIN: { speed: 0.15, max-travel: 12, hit-radius: 1.5, damage-mode: WEAPON, damage: 7.0, knockback: 1.3, knockback-base: 0.45, lock: 20, lock-delay: 5, nausea-effect: "CONFUSION", nausea-duration: 100, particle: REDSTONE, r: 120, g: 200, b: 255, size: 1.2 } }
            """;

    /** The fixed swing damage the Javelin prices at throw — single-sourced into every javelin-damage expectation. */
    private static final double SWING = 6.0;
    private static final WeaponDamage WEAPON = p -> SWING;

    private final Plugin plugin;

    public ReforgeMotionSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-reforge-motion-suite");
            writeDef(root, "singularity", SINGULARITY);
            writeDef(root, "singularity-quick", SINGULARITY_QUICK);
            writeDef(root, "leviathans-reach", LEVIATHAN);
            writeDef(root, "blink", BLINK);
            writeDef(root, "castling", CASTLING);
            writeDef(root, "javelin", JAVELIN);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "reforge defs failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        CombatCodec combat = new CombatCodec(keys.combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(combat, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews,
                triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        // A constant tick keeps a gate-6 cooldown reading its full remainder (the no-refund proof) and needs no
        // clock task — the machines drive themselves off the real scheduler, not the sink env clock.
        SinkEnv env = SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(), () -> 0L);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());

        Deps deps = new Deps(dispatch, holder, resolvers, Messages.defaults());
        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();

        blinkLandsShortOfAWall(h, deps, world, spawn);
        blinkZeroAgainstPointBlankWall(h, deps, world, spawn);
        blinkNeverPhasesIntoTerrain(h, deps, world, spawn);
        blinkPitchedLookStillTravels(h, deps, world, spawn);
        grappleReelsTheEntityInSight(h, deps, world, spawn);
        grappleZipsToTerrainWhenNoEntity(h, deps, world, spawn);
        grappleWallShieldedEntityIsNotReeled(h, deps, world, spawn);
        singularityBeamPicksTheSightedBlock(h, deps, world, spawn);
        singularityPullsVictimsInward(h, deps, world, spawn);
        singularityImplodesWithFalloffAndAttribution(h, deps, world, spawn);
        singularityPullsTheCasterToo(h, deps, world, spawn);
        castlingSwapCompletesWithVelocitiesZeroed(h, deps, world, spawn);
        castlingLosBreakAborts(h, deps, world, spawn);
        castlingCountdownCuesAreAudible(h, deps, world, spawn);
        castlingCooldownStaysSpentOnAbort(h, deps, world, spawn);
        javelinTravelsAtAuthoredSpeedAndMisses(h, deps, world, spawn);
        javelinHitsFirstVictimWithSwingDamage(h, deps, world, spawn);
        javelinKnockbackReversedAlongFlight(h, deps, world, spawn);
        javelinVictimHeldForLockTicks(h, deps, world, spawn);
        javelinWallStopsTheFlight(h, deps, world, spawn);
        crossRegionAttributionDegradesOffRegion(h, deps, world, spawn);
    }

    // ── BLINK — a real engine kind through fireUse against the true block walk ─────────────────────

    private void blinkLandsShortOfAWall(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.blink.landsShortOfAWall";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_WALL);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_blk1");
            if (caster == null) {
                return;
            }
            // A wall two blocks along +X (feet + head): the walk must stop at the last open cell before it.
            solid(world, bx + 2, by, bz);
            solid(world, bx + 2, by + 1, bz);
            Location stand = centerFacing(arena, -90.0f); // yaw -90 → facing +X
            caster.setGravity(false);
            place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                deps.dispatch().fireUse(caster, keys(deps, "blink"));
                awaitUntil(caster, () -> forwardX(caster, stand) >= 1.0, 0, 30, moved -> {
                    h.guard(key, () -> {
                        double dx = forwardX(caster, stand);
                        if (dx < 1.0 || dx >= 2.0) {
                            throw new IllegalStateException("blink landed " + dx
                                    + " blocks along facing; expected [1, 2) short of the 2-block wall");
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    private void blinkZeroAgainstPointBlankWall(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.blink.zeroBlinkAgainstPointBlankWall";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_ZERO);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_blk2");
            if (caster == null) {
                return;
            }
            solid(world, bx + 1, by, bz); // the adjacent cell: the first sample is blocked → zero blink
            solid(world, bx + 1, by + 1, bz);
            Location stand = centerFacing(arena, -90.0f);
            caster.setGravity(false);
            place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                deps.dispatch().fireUse(caster, keys(deps, "blink"));
                Scheduling.onEntityLater(caster, 12L, () -> { h.guard(key, () -> {
                    double dx = forwardX(caster, stand);
                    if (Math.abs(dx) > POS_EPS) {
                        throw new IllegalStateException("a point-blank wall still moved the caster " + dx + " blocks");
                    }
                }); rig.teardown(); });
            }));
        });
    }

    private void blinkNeverPhasesIntoTerrain(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.blink.neverPhasesIntoTerrain";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_PHASE);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_blk3");
            if (caster == null) {
                return;
            }
            solid(world, bx + 2, by, bz);
            solid(world, bx + 2, by + 1, bz);
            Location stand = centerFacing(arena, -90.0f);
            caster.setGravity(false);
            place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                deps.dispatch().fireUse(caster, keys(deps, "blink"));
                awaitUntil(caster, () -> forwardX(caster, stand) >= 1.0, 0, 30, moved -> {
                    h.guard(key, () -> {
                        Location at = caster.getLocation();
                        Block feet = at.getBlock();
                        Block head = feet.getRelative(0, 1, 0);
                        if (!feet.isPassable() || !head.isPassable()) {
                            throw new IllegalStateException("blink landed inside terrain: feet="
                                    + feet.getType() + " head=" + head.getType());
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    private void blinkPitchedLookStillTravels(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.blink.pitchedLookStillTravels";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_PITCH);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_blk4");
            if (caster == null) {
                return;
            }
            // A real floor under the whole path: the 1.10 regression was a pitched ray grounding in the
            // floor cell within the first sample — a zero blink on every grounded look-down. Real players
            // look slightly down almost always, so this is THE real-play posture (fake casters default to
            // pitch 0, which is exactly how the bug shipped green).
            for (int i = 0; i <= 6; i++) {
                solid(world, bx + i, by - 1, bz);
            }
            Location stand = centerFacing(arena, -90.0f); // facing +X
            stand.setPitch(35.0f);                        // looking down at the ground ahead
            caster.setGravity(false);
            place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                deps.dispatch().fireUse(caster, keys(deps, "blink"));
                awaitUntil(caster, () -> forwardX(caster, stand) >= 3.0, 0, 30, moved -> {
                    h.guard(key, () -> {
                        double dx = forwardX(caster, stand);
                        if (dx < 3.0) {
                            throw new IllegalStateException("a pitched look zeroed the blink again: moved "
                                    + dx + " blocks of the authored 4");
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    // ── GRAPPLE — service resolves both stubbed rays, sink reels/zips ──────────────────────────────

    private void grappleReelsTheEntityInSight(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.grapple.reelsTheEntityInSight";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_GRAPPLE_REEL);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_grp1");
            if (caster == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);         // facing +X
            Location cowAt = stand.clone().add(8, 0, 0);          // 8 blocks along facing, open line
            Location reelTo = stand.clone().add(2, 0, 0);         // reel-distance 2 in front (feet Y)
            caster.setGravity(false);
            place(caster, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                GrappleService grapple = new GrappleService(deps.dispatch(),
                        (p, r) -> cow, (p, r) -> null, deps.messages()); // entity ray hits the cow, no terrain
                grapple.start(caster, effect(deps, "leviathans-reach", GrappleEffect.HEAD));
                awaitUntil(cow, () -> horiz(cow.getLocation(), reelTo) <= 1.0, 0, 30, reeled -> {
                    h.guard(key, () -> {
                        if (horiz(cow.getLocation(), reelTo) > 1.0) {
                            throw new IllegalStateException("the hooked cow was not reeled to the front spot: "
                                    + horiz(cow.getLocation(), reelTo) + " blocks off");
                        }
                        if (cow.getVelocity().length() > 0.35) {
                            throw new IllegalStateException("the reeled cow kept velocity " + cow.getVelocity());
                        }
                        if (cow.getActivePotionEffects().isEmpty()) {
                            throw new IllegalStateException("the reeled cow got no slowness");
                        }
                    });
                    rig.teardown();
                });
            })));
        });
    }

    private void grappleZipsToTerrainWhenNoEntity(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.grapple.zipsToTerrainWhenNoEntity";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_GRAPPLE_ZIP);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_grp2");
            if (caster == null) {
                return;
            }
            Location stand = centerFacing(arena, -90.0f);
            Block hook = world.getBlockAt(arena.getBlockX() + 10, arena.getBlockY(), arena.getBlockZ());
            caster.setGravity(false);
            place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                GrappleService grapple = new GrappleService(deps.dispatch(),
                        (p, r) -> null, (p, r) -> hook, deps.messages()); // no entity, terrain at 10 → zip the caster
                grapple.start(caster, effect(deps, "leviathans-reach", GrappleEffect.HEAD));
                // Terrain zip is a launched velocity, applied flightTicks (~5) after the emit.
                awaitUntil(caster, () -> caster.getVelocity().getX() > 0.05, 0, 20, zipped -> {
                    h.guard(key, () -> {
                        if (caster.getVelocity().getX() <= 0.05) {
                            throw new IllegalStateException("the caster never zipped toward the terrain point: v="
                                    + caster.getVelocity());
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    private void grappleWallShieldedEntityIsNotReeled(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.grapple.wallShieldedEntityIsNotReeled";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_GRAPPLE_WALL);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_grp3");
            if (caster == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            Location cowAt = stand.clone().add(6, 0, 0);          // behind the wall, further than it
            Block wall = world.getBlockAt(arena.getBlockX() + 4, arena.getBlockY(), arena.getBlockZ());
            caster.setGravity(false);
            place(caster, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                // The closer-of rule: the wall block (4) is nearer than the cow (6), so terrain mode wins and
                // the shielded cow is untouched — the same rule that compensates the 1.8 dot-scan's wall-blindness.
                GrappleService grapple = new GrappleService(deps.dispatch(),
                        (p, r) -> cow, (p, r) -> wall, deps.messages());
                grapple.start(caster, effect(deps, "leviathans-reach", GrappleEffect.HEAD));
                Scheduling.onEntityLater(caster, 18L, () -> { h.guard(key, () -> {
                    if (horiz(cow.getLocation(), cowAt) > POS_EPS) {
                        throw new IllegalStateException("the wall-shielded cow was reeled " + horiz(cow.getLocation(), cowAt)
                                + " blocks");
                    }
                    if (caster.getVelocity().getX() <= 0.05) {
                        throw new IllegalStateException("the caster did not zip to the nearer wall: v=" + caster.getVelocity());
                    }
                }); rig.teardown(); });
            })));
        });
    }

    // ── GRAVITY_WELL (Singularity) — service arms the well from the stubbed sighted block ──────────

    private void singularityBeamPicksTheSightedBlock(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.singularity.beamPicksTheSightedBlock";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_SING_BEAM);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_sng1");
            if (caster == null) {
                return;
            }
            Block sighted = world.getBlockAt(arena.getBlockX() + 4, arena.getBlockY() - 3, arena.getBlockZ());
            Location core = coreOf(sighted);
            LivingEntity cow = staticCow(rig, world, arena);
            Location cowAt = core.clone().add(3, 0, 0); // east of the core → a pull points it back in −X
            caster.setGravity(false);
            place(caster, arena.clone().add(0, 0, 8), () -> place(cow, cowAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                GravityWellService svc = new GravityWellService(deps.dispatch(), (p, r) -> sighted,
                        deps.resolvers());
                svc.start(caster, effect(deps, "singularity", GravityWellEffect.HEAD));
                // The pull is a per-pulse VELOCITY toward the core (the real-player mechanic); NoAI cows and
                // clientless players never translate velocity to a position on the server, so the well's
                // formation above the sighted block is proven by the −X pull velocity it imparts (the §B1.15
                // rule, and exactly how grapple-zip reads its launch). A stray core would pull some other way.
                awaitUntil(cow, () -> cow.getVelocity().getX() < -0.05, 0, 40, pulled -> {
                    h.guard(key, () -> {
                        if (cow.getVelocity().getX() >= -0.05) {
                            throw new IllegalStateException("the well did not form above the sighted block: cow "
                                    + "pull velocity x=" + cow.getVelocity().getX() + " (expected < 0 toward the core)");
                        }
                    });
                    rig.teardown();
                });
            })));
        });
    }

    private void singularityPullsVictimsInward(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.singularity.pullsVictimsInward";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_SING_PULL);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_sng2");
            if (caster == null) {
                return;
            }
            Block sighted = world.getBlockAt(arena.getBlockX(), arena.getBlockY() - 3, arena.getBlockZ());
            Location core = coreOf(sighted);
            LivingEntity east = staticCow(rig, world, arena);
            LivingEntity west = staticCow(rig, world, arena);
            Location eastAt = core.clone().add(4, 0, 0);
            Location westAt = core.clone().add(-4, 0, 0); // opposite sides — a sign error fails one of them
            caster.setGravity(false);
            place(caster, arena.clone().add(0, 0, 8), () -> place(east, eastAt, () -> place(west, westAt, () ->
                    Scheduling.onEntityLater(caster, 5L, () -> {
                        GravityWellService svc = new GravityWellService(deps.dispatch(), (p, r) -> sighted,
                                deps.resolvers());
                        svc.start(caster, effect(deps, "singularity", GravityWellEffect.HEAD));
                        // The pull imparts a toward-core VELOCITY every pulse; NoAI cows don't move from velocity
                        // (only teleports relocate them — see grapple-reel), so the inward drag is read as the
                        // pull velocity itself. Opposite sides catch a sign error: east must gain −X, west +X.
                        Scheduling.onEntityLater(caster, 24L, () -> pullVelReadout(east, west, (ve, vw) -> {
                                h.guard(key, () -> {
                                    if (ve >= -0.05 || vw <= 0.05) {
                                        throw new IllegalStateException("a cow was not dragged inward: east v.x="
                                                + ve + " west v.x=" + vw + " (expected east<0, west>0 toward core)");
                                    }
                                }); rig.teardown(); }));
                    }))));
        });
    }

    private void singularityImplodesWithFalloffAndAttribution(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.singularity.implodesWithFalloffAndAttribution";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_SING_IMPLODE);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_sng3");
            Player victim = spawnFake(h, key, rig, world, "se_rf_sng3v");
            if (caster == null || victim == null) {
                return;
            }
            Block sighted = world.getBlockAt(arena.getBlockX(), arena.getBlockY() - 3, arena.getBlockZ());
            Location core = coreOf(sighted);
            LivingEntity near = staticCow(rig, world, arena);
            LivingEntity far = staticCow(rig, world, arena);
            Location nearAt = core.clone().add(0.5, 0, 0); // ~full damage
            Location farAt = core.clone().add(5, 0, 0);    // floored falloff
            Location victimAt = core.clone().add(1, 0, 0);
            AtomicBoolean attributed = new AtomicBoolean();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onHit(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victim.getUniqueId())
                            && e.getDamager().getUniqueId().equals(caster.getUniqueId())) {
                        attributed.set(true);
                    }
                }
            });
            caster.setGravity(false);
            victim.setGravity(false);
            place(caster, arena.clone().add(0, 0, 8), () -> place(victim, victimAt, () ->
                    place(near, nearAt, () -> place(far, farAt, () ->
                            Scheduling.onEntityLater(caster, SPAWN_INVULN_TICKS, () -> {
                                double nearBefore = near.getHealth();
                                double farBefore = far.getHealth();
                                near.setNoDamageTicks(0);
                                far.setNoDamageTicks(0);
                                Scheduling.onEntity(victim, () -> victim.setNoDamageTicks(0));
                                GravityWellService svc = new GravityWellService(deps.dispatch(), (p, r) -> sighted,
                                        deps.resolvers());
                                svc.start(caster, effect(deps, "singularity-quick", GravityWellEffect.HEAD));
                                Scheduling.onEntityLater(caster, 20L, () ->
                                        implodeReadout(rig, near, far, (nearNow, farNow) -> { h.guard(key, () -> {
                                            double nearDelta = nearBefore - nearNow;
                                            double farDelta = farBefore - farNow;
                                            if (nearDelta < 8.0 * 0.75) {
                                                throw new IllegalStateException("near cow took only " + nearDelta
                                                        + " (expected ~8 at the core)");
                                            }
                                            if (farDelta < 8.0 * 0.25 - HEALTH_EPS) {
                                                throw new IllegalStateException("far cow took " + farDelta
                                                        + " below the 0.25 falloff floor (" + (8.0 * 0.25) + ")");
                                            }
                                            if (nearDelta <= farDelta) {
                                                throw new IllegalStateException("falloff inverted: near=" + nearDelta
                                                        + " far=" + farDelta);
                                            }
                                            if (!attributed.get()) {
                                                throw new IllegalStateException("the player victim's implosion hit was "
                                                        + "not attributed to the caster");
                                            }
                                        }); rig.teardown(); }));
                            })))));
        });
    }

    private void singularityPullsTheCasterToo(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.singularity.pullsTheCasterToo";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_SING_SELF);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_sng4");
            if (caster == null) {
                return;
            }
            Block sighted = world.getBlockAt(arena.getBlockX(), arena.getBlockY() - 3, arena.getBlockZ());
            Location core = coreOf(sighted);
            Location standAt = core.clone().add(3, 0, 0); // east of the core → self-pull drags the caster back in −X
            caster.setGravity(false);
            place(caster, standAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                GravityWellService svc = new GravityWellService(deps.dispatch(), (p, r) -> sighted,
                        deps.resolvers());
                svc.start(caster, effect(deps, "singularity", GravityWellEffect.HEAD));
                // A clientless caster never moves from velocity, so the authored self-pull downside is proven by
                // the −X drag velocity it gains (the §B1.15 rule; grapple-zip reads a fake player's launch the
                // same way).
                awaitUntil(caster, () -> caster.getVelocity().getX() < -0.05, 0, 40, pulled -> {
                        h.guard(key, () -> {
                            if (caster.getVelocity().getX() >= -0.05) {
                                throw new IllegalStateException("self-pull did not drag the caster inward: "
                                        + "pull velocity x=" + caster.getVelocity().getX() + " (expected < 0 toward core)");
                            }
                        }); rig.teardown(); });
            }));
        });
    }

    // ── SWAP_POSITION (Castling) — channel machine on the caster's scheduler ───────────────────────

    private void castlingSwapCompletesWithVelocitiesZeroed(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.castling.swapCompletesWithVelocitiesZeroed";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_CAST_SWAP);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_cst1");
            Player victim = spawnFake(h, key, rig, world, "se_rf_cst1v");
            if (caster == null || victim == null) {
                return;
            }
            Location casterAt = centerFacing(arena, 0.0f);              // distinct yaws — a transposition guard
            Location victimAt = arena.clone().add(8, 0, 0);
            victimAt.setYaw(180.0f);
            victimAt.add(0.5, 0, 0.5);
            caster.setGravity(false);
            victim.setGravity(false);
            place(caster, casterAt, () -> place(victim, victimAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                CastlingService svc = new CastlingService(deps.dispatch(), (p, r) -> victim,
                        deps.messages(), deps.resolvers());
                svc.start(caster, effect(deps, "castling", SwapPositionEffect.HEAD));
                Scheduling.onEntityLater(caster, 60L, () -> { h.guard(key, () -> {
                    if (horiz(caster.getLocation(), victimAt) > 1.0) {
                        throw new IllegalStateException("caster did not land at the victim's spot: "
                                + horiz(caster.getLocation(), victimAt));
                    }
                    if (Math.abs(caster.getLocation().getYaw() - 0.0f) > 1.0f) {
                        throw new IllegalStateException("caster lost its own facing: " + caster.getLocation().getYaw());
                    }
                    if (caster.getVelocity().length() > 0.1 || victim.getVelocity().length() > 0.1) {
                        throw new IllegalStateException("a swapped party kept velocity");
                    }
                }); rig.teardown(); });
            })));
        });
    }

    private void castlingLosBreakAborts(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.castling.losBreakAborts";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_CAST_LOS);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_cst2");
            Player victim = spawnFake(h, key, rig, world, "se_rf_cst2v");
            if (caster == null || victim == null) {
                return;
            }
            Location casterAt = centerFacing(arena, -90.0f);
            Location victimAt = arena.clone().add(8, 0, 0).add(0.5, 0, 0.5);
            caster.setGravity(false);
            victim.setGravity(false);
            place(caster, casterAt, () -> place(victim, victimAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                CastlingService svc = new CastlingService(deps.dispatch(), (p, r) -> victim,
                        deps.messages(), deps.resolvers());
                svc.start(caster, effect(deps, "castling", SwapPositionEffect.HEAD));
                // Mid-channel: raise a wall between them so the next hasLineOfSight probe aborts the channel.
                Scheduling.onEntityLater(caster, 20L, () -> {
                    int wx = arena.getBlockX() + 4;
                    solid(world, wx, arena.getBlockY(), arena.getBlockZ());
                    solid(world, wx, arena.getBlockY() + 1, arena.getBlockZ());
                    solid(world, wx, arena.getBlockY() + 2, arena.getBlockZ());
                    Scheduling.onEntityLater(caster, 40L, () -> { h.guard(key, () -> {
                        if (horiz(caster.getLocation(), casterAt) > POS_EPS
                                || horiz(victim.getLocation(), victimAt) > POS_EPS) {
                            throw new IllegalStateException("a broken-LOS channel still swapped: caster moved "
                                    + horiz(caster.getLocation(), casterAt) + ", victim moved "
                                    + horiz(victim.getLocation(), victimAt));
                        }
                    }); rig.teardown(); });
                });
            })));
        });
    }

    private void castlingCountdownCuesAreAudible(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.castling.countdownCuesAreAudible";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_CAST_CUES);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_cst3");
            Player victim = spawnFake(h, key, rig, world, "se_rf_cst3v");
            if (caster == null || victim == null) {
                return;
            }
            Location casterAt = centerFacing(arena, 0.0f);
            Location victimAt = arena.clone().add(8, 0, 0).add(0.5, 0, 0.5);
            caster.setGravity(false);
            victim.setGravity(false);
            // The countdown announces the caster once per whole-second change; a clientless test cannot hear the
            // sound cue, so we count the paired second-message the same call sends and assert its cadence.
            AtomicInteger cues = new AtomicInteger();
            Messages counting = new Messages(Lang::defaults, () -> "", (BooleanSupplier) () -> true,
                    (BiFunction<Player, String, String>) (p, text) -> {
                        if (p.getUniqueId().equals(caster.getUniqueId())) {
                            cues.incrementAndGet();
                        }
                        return text;
                    });
            place(caster, casterAt, () -> place(victim, victimAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                CastlingService svc = new CastlingService(deps.dispatch(), (p, r) -> victim, counting, deps.resolvers());
                svc.start(caster, effect(deps, "castling", SwapPositionEffect.HEAD));
                // Read at 30t: past the 2s→1s change (~20t) but before the swap at 40t adds its own message.
                Scheduling.onEntityLater(caster, 30L, () -> { h.guard(key, () -> {
                    if (cues.get() != 2) {
                        throw new IllegalStateException("a 40t (2s) channel announced " + cues.get()
                                + " second-cues, expected 2");
                    }
                }); rig.teardown(); });
            })));
        });
    }

    private void castlingCooldownStaysSpentOnAbort(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.castling.cooldownStaysSpentOnAbort";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_CAST_CD);
        rig.onArena(arena, () -> {
            Player caster = spawnFake(h, key, rig, world, "se_rf_cst4");
            if (caster == null) {
                return;
            }
            caster.setGravity(false);
            place(caster, centerFacing(arena, 0.0f), () -> Scheduling.onEntityLater(caster, 5L, () -> {
                // Fire #1 arms gate-6 cooldown, then the machine aborts with no crosshair target (no refund).
                UseAttempt first = deps.dispatch().fireUse(caster, keys(deps, "castling"));
                CastlingService svc = new CastlingService(deps.dispatch(), (p, r) -> null,
                        deps.messages(), deps.resolvers());
                svc.start(caster, effect(deps, "castling", SwapPositionEffect.HEAD));
                UseAttempt second = deps.dispatch().fireUse(caster, keys(deps, "castling"));
                h.guard(key, () -> {
                    if (!first.activated()) {
                        throw new IllegalStateException("the first castling did not activate to arm the cooldown");
                    }
                    if (!second.onCooldown() || second.cooldownRemainingTicks() <= 0) {
                        throw new IllegalStateException("the cooldown was refunded after an abort: onCooldown="
                                + second.onCooldown() + " remaining=" + second.cooldownRemainingTicks());
                    }
                });
                rig.teardown();
            }));
        });
    }

    // ── JAVELIN — flight, hit pricing, knockback and camera-lock hold ──────────────────────────────

    private void javelinTravelsAtAuthoredSpeedAndMisses(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.javelin.travelsAtAuthoredSpeedAndMisses";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_JAV_MISS);
        rig.onArena(arena, () -> {
            Player thrower = spawnFake(h, key, rig, world, "se_rf_jav1");
            if (thrower == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            Location cowAt = stand.clone().add(20, 1.4, 0); // beyond max-travel 12 → never reached
            thrower.setGravity(false);
            place(thrower, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(thrower, 5L, () -> {
                double before = cow.getHealth();
                new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(), () -> true, () -> true, (a, b) -> false)
                        .start(thrower, effect(deps, "javelin", JavelinEffect.HEAD));
                // Budget = ceil(12 / 0.15) = 80 steps; give it well past the miss teardown.
                Scheduling.onEntityLater(cow, 100L, () -> { h.guard(key, () -> {
                    if (cow.getHealth() < before - HEALTH_EPS) {
                        throw new IllegalStateException("a javelin past its 12-block budget still hit a cow at 20");
                    }
                }); rig.teardown(); });
            })));
        });
    }

    private void javelinHitsFirstVictimWithSwingDamage(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.javelin.hitsFirstVictimWithSwingDamage";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_JAV_HIT);
        rig.onArena(arena, () -> {
            Player thrower = spawnFake(h, key, rig, world, "se_rf_jav2");
            if (thrower == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            Location cowAt = stand.clone().add(6, 0.72, 0); // centre near eye height, 6 blocks along the flight
            thrower.setGravity(false);
            place(thrower, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(thrower, 5L, () -> {
                double before = cow.getHealth();
                double expected = WEAPON.swingDamage(thrower); // single-sourced through the same seam the service uses
                cow.setNoDamageTicks(0);
                new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(), () -> true, () -> true, (a, b) -> false)
                        .start(thrower, effect(deps, "javelin", JavelinEffect.HEAD));
                // Speed proof: at 0.15/tick a hit-radius-1.5 javelin registers on a 6-block cow near t=26 (the tip
                // reaches within radius ~1.95 blocks short of the cow centre), so the "still airborne" read must sit
                // BEFORE that — t=18 (tip only ~2.9 blocks out) — with the strike confirmed well after.
                Scheduling.onEntityLater(cow, 18L, () -> {
                    boolean earlyHit = cow.getHealth() < before - HEALTH_EPS;
                    Scheduling.onEntityLater(cow, 42L, () -> { h.guard(key, () -> {
                        if (earlyHit) {
                            throw new IllegalStateException("the javelin reached a 6-block cow before t=18 (too fast)");
                        }
                        double delta = before - cow.getHealth();
                        if (Math.abs(delta - expected) > HEALTH_EPS) {
                            throw new IllegalStateException("javelin dealt " + delta + " not the swing damage " + expected);
                        }
                    }); rig.teardown(); });
                });
            })));
        });
    }

    private void javelinKnockbackReversedAlongFlight(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.javelin.knockbackReversedAlongFlight";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_JAV_KB);
        rig.onArena(arena, () -> {
            Player thrower = spawnFake(h, key, rig, world, "se_rf_jav3");
            if (thrower == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            Location cowAt = stand.clone().add(6, 0.72, 0);
            thrower.setGravity(false);
            place(thrower, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(thrower, 5L, () -> {
                cow.setNoDamageTicks(0);
                new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(), () -> true, () -> true, (a, b) -> false)
                        .start(thrower, effect(deps, "javelin", JavelinEffect.HEAD));
                // Knockback is a VELOCITY along the flight (+X); a NoAI cow never converts it to a position, and the
                // camera-lock pin zeroes velocity ~5 ticks after impact, so poll every tick for the impulse itself.
                awaitUntil(cow, () -> cow.getVelocity().getX() > 0.2, 0, 70, knocked -> {
                        h.guard(key, () -> {
                            double along = cow.getVelocity().getX(); // flight is +X → dot is the x-velocity
                            if (along <= 0.2) {
                                throw new IllegalStateException("the javelin did not knock the victim along its flight: "
                                        + along);
                            }
                        }); rig.teardown(); });
            })));
        });
    }

    private void javelinVictimHeldForLockTicks(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.javelin.victimHeldForLockTicks";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_JAV_HOLD);
        rig.onArena(arena, () -> {
            Player thrower = spawnFake(h, key, rig, world, "se_rf_jav4");
            Player victim = spawnFake(h, key, rig, world, "se_rf_jav4v");
            if (thrower == null || victim == null) {
                return;
            }
            Location stand = centerFacing(arena, -90.0f);
            Location victimAt = stand.clone().add(6, 0, 0).add(0, 0, 0);
            Location shove = victimAt.clone().add(0, 0, 6); // a mid-hold teleport the pin must re-assert against
            thrower.setGravity(false);
            victim.setGravity(false);
            place(thrower, stand, () -> place(victim, victimAt.clone().add(0, 0.0, 0), () ->
                    Scheduling.onEntityLater(thrower, SPAWN_INVULN_TICKS, () -> {
                        Scheduling.onEntity(victim, () -> victim.setNoDamageTicks(0));
                        double before = victim.getHealth();
                        new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(), () -> true, () -> true, (a, b) -> false)
                                .start(thrower, effect(deps, "javelin", JavelinEffect.HEAD));
                        // The javelin's flight time depends on hit-radius, not just distance, so everything downstream
                        // is timed off the IMPACT (health drop), not a fixed tick — the old fixed t+55 read landed
                        // after the pin window and the shove stuck (drift 6.0). Impact → pin arms at +lock-delay(5),
                        // holds for lock(20), nausea lands at +lock-delay+lock(25).
                        awaitUntil(victim, () -> victim.getHealth() < before - HEALTH_EPS, 0, 80, hit -> {
                            if (!hit) {
                                h.fail(key, "the javelin never struck the held victim");
                                rig.teardown();
                                return;
                            }
                            Scheduling.onEntityLater(victim, 8L, () -> { // impact+8: firmly inside [+5, +25]
                                AtomicReference<Location> held = new AtomicReference<>(victim.getLocation().clone());
                                boolean nauseaMidHold = !victim.getActivePotionEffects().isEmpty();
                                Scheduling.onEntity(victim, () -> victim.teleportAsync(shove)); // pin must snap it back
                                Scheduling.onEntityLater(victim, 4L, () -> { // impact+12: still pinned
                                    double drift = horiz(victim.getLocation(), held.get());
                                    // Past release (impact+25) + nausea landing: a teleport now sticks and nausea is on.
                                    Scheduling.onEntityLater(victim, 22L, () -> { // impact+34
                                        boolean nauseaAfter = !victim.getActivePotionEffects().isEmpty();
                                        Location free = victim.getLocation().clone().add(0, 0, 5);
                                        Scheduling.onEntity(victim, () -> victim.teleportAsync(free));
                                        Scheduling.onEntityLater(victim, 4L, () -> { h.guard(key, () -> {
                                            if (drift > 1.0) {
                                                throw new IllegalStateException("the hold did not pin the victim through "
                                                        + "a staged teleport: drifted " + drift);
                                            }
                                            if (nauseaMidHold) {
                                                throw new IllegalStateException("nausea landed DURING the hold (owner "
                                                        + "LAW: freeze, THEN nausea)");
                                            }
                                            if (!nauseaAfter) {
                                                throw new IllegalStateException("nausea never landed after the hold released");
                                            }
                                            if (horiz(victim.getLocation(), free) > 1.0) {
                                                throw new IllegalStateException("the victim was still pinned after release");
                                            }
                                        }); rig.teardown(); });
                                    });
                                });
                            });
                        });
                    })));
        });
    }

    private void javelinWallStopsTheFlight(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.javelin.wallStopsTheFlight";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_JAV_WALL);
        rig.onArena(arena, () -> {
            Player thrower = spawnFake(h, key, rig, world, "se_rf_jav5");
            if (thrower == null) {
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            Location cowAt = stand.clone().add(8, 1.4, 0); // behind the wall
            int wx = arena.getBlockX() + 5;
            int wy = arena.getBlockY() + 1; // eye-line block (feet Y + 1)
            thrower.setGravity(false);
            solid(world, wx, arena.getBlockY(), arena.getBlockZ());
            solid(world, wx, wy, arena.getBlockZ());
            solid(world, wx, wy + 1, arena.getBlockZ());
            place(thrower, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(thrower, 5L, () -> {
                double before = cow.getHealth();
                new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(), () -> true, () -> true, (a, b) -> false)
                        .start(thrower, effect(deps, "javelin", JavelinEffect.HEAD));
                Scheduling.onEntityLater(cow, 70L, () -> { h.guard(key, () -> {
                    if (cow.getHealth() < before - HEALTH_EPS) {
                        throw new IllegalStateException("the javelin passed through the wall and hit the shielded cow");
                    }
                }); rig.teardown(); });
            })));
        });
    }

    // ── Folia cross-region attribution degrade (skips clean on Paper) ──────────────────────────────

    private void crossRegionAttributionDegradesOffRegion(Harness h, Deps deps, World world, Location spawn) {
        final String key = "reforge.crossregion.attributionDegradesOffRegion";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_XREGION);
        Location far = arena.clone().add(CrossRegion.GAP, 0, CrossRegion.GAP);
        rig.onArena(arena, far, () -> {
            // Single-threaded Paper has no regions to cross — the degrade is vacuous, like the other X-region suites.
            if (!Capabilities.foliaPresent()) {
                plugin.getLogger().info("[reforge] single-threaded Paper — cross-region degrade not applicable");
                h.pass(key);
                return;
            }
            LivingEntity cow = staticCow(rig, world, arena);
            Block sighted = world.getBlockAt(arena.getBlockX(), arena.getBlockY() - 3, arena.getBlockZ());
            Location core = coreOf(sighted);
            Location cowAt = core.clone().add(1, 0, 0);
            // FakePlayers registers the player into the WORLD-SPAWN chunk, which this primary region owns but the
            // far region (GAP blocks away, a distinct Folia region) does not — spawning on the far region thread
            // threw "Cannot add entity off-main thread". So spawn here on the primary thread and teleport out.
            Player caster;
            try {
                caster = rig.spawnFake(world, "se_rf_xr");
            } catch (Throwable t) {
                h.fail(key, "fake-player spawn: " + t);
                rig.teardown();
                return;
            }
            caster.setGravity(false);
            place(cow, cowAt, () -> place(caster, far, () -> Scheduling.onEntityLater(caster, 10L, () -> {
                // Prime the cow's baseline and schedule the read on ITS OWN (primary) thread — never cross-region;
                // the well is armed on the caster's (far) thread, exactly as production's activation hook would fire.
                Scheduling.onEntity(cow, () -> {
                    double before = cow.getHealth();
                    cow.setNoDamageTicks(0);
                    Scheduling.onEntityLater(cow, 30L, () -> { h.guard(key, () -> {
                        if (cow.getHealth() >= before - HEALTH_EPS) {
                            throw new IllegalStateException("the cross-region implosion never landed a bare hurt");
                        }
                    }); rig.teardown(); });
                });
                // The well core lives in the primary region; the owner sits in the far one, so at implosion the
                // guarded ownership read is false and the hurt degrades to bare rather than throwing.
                GravityWellService svc = new GravityWellService(deps.dispatch(), (p, r) -> sighted,
                        deps.resolvers());
                svc.start(caster, effect(deps, "singularity-quick", GravityWellEffect.HEAD));
            })));
        });
    }

    // ── Staging + geometry helpers ─────────────────────────────────────────────────────────────────

    /** A chunk-centred sky arena for scenario {@code index}: far from spawn and from every other scenario. */
    private static Location arena(World world, Location spawn, int index) {
        int rawX = spawn.getBlockX() + ARENA_BASE + index * ARENA_STEP;
        int rawZ = spawn.getBlockZ() + ARENA_BASE;
        int bx = (rawX & ~15) + 8; // chunk-mid so ±7 block staging stays in the one force-loaded chunk
        int bz = (rawZ & ~15) + 8;
        return new Location(world, bx, ARENA_Y, bz);
    }

    /** The arena centre as an entity stand point (block-centred) with the given yaw, level pitch. */
    private static Location centerFacing(Location arena, float yaw) {
        return new Location(arena.getWorld(), arena.getBlockX() + 0.5, arena.getBlockY(),
                arena.getBlockZ() + 0.5, yaw, 0.0f);
    }

    /** The Singularity core the service computes: block centre + (0, 1 + rise, 0). */
    private static Location coreOf(Block block) {
        return block.getLocation().clone().add(0.5, 1.0 + SINGULARITY_RISE, 0.5);
    }

    private static void solid(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.STONE);
    }

    /** Spawn a pinned cow at the arena (AI + gravity off so its staged position holds for the assertion). */
    private LivingEntity staticCow(CombatRig rig, World world, Location arena) {
        LivingEntity cow = rig.spawn(world, arena.clone().add(0.5, 0, 0.5), EntityType.COW, LivingEntity.class);
        cow.setAI(false);
        cow.setGravity(false);
        cow.setNoDamageTicks(0);
        return cow;
    }

    private Player spawnFake(Harness h, String key, CombatRig rig, World world, String name) {
        try {
            return rig.spawnFake(world, name);
        } catch (Throwable t) {
            h.fail(key, "fake-player spawn: " + t);
            rig.teardown();
            return null;
        }
    }

    private static double forwardX(Player p, Location from) {
        return p.getLocation().getX() - from.getX();
    }

    private static double horiz(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Read both pull-cow x-velocities on each cow's own thread (hopping east→west), then hand them over. */
    private void pullVelReadout(LivingEntity east, LivingEntity west,
                               java.util.function.BiConsumer<Double, Double> then) {
        Scheduling.onEntity(east, () -> {
            double ve = east.getVelocity().getX();
            Scheduling.onEntity(west, () -> then.accept(ve, west.getVelocity().getX()));
        });
    }

    /** Read both implosion cows' health, hopping to each in turn. */
    private void implodeReadout(CombatRig rig, LivingEntity near, LivingEntity far,
                                java.util.function.BiConsumer<Double, Double> then) {
        Scheduling.onEntity(near, () -> {
            double nearNow = near.getHealth();
            Scheduling.onEntity(far, () -> then.accept(nearNow, far.getHealth()));
        });
    }

    // ── Effect / def plumbing (the ReforgeMachines.onActivated walk, restricted to one head) ────────

    private List<String> keys(Deps deps, String stem) {
        return deps.holder().library().reforgeDefOf("reforges/" + stem).useStableKeys();
    }

    /** The compiled effect for {@code head} in {@code stem}'s USE abilities against the current snapshot. */
    private CompiledEffect effect(Deps deps, String stem, String head) {
        ReforgeDef def = deps.holder().library().reforgeDefOf("reforges/" + stem);
        Snapshot snapshot = deps.holder().snapshot();
        for (String key : def.useStableKeys()) {
            Ability ability = snapshot.byStableKey(key);
            if (ability == null) {
                continue;
            }
            for (CompiledEffect fx : ability.effects()) {
                if (head.equals(fx.head())) {
                    return fx;
                }
            }
        }
        throw new IllegalStateException("no " + head + " effect in reforges/" + stem);
    }

    private static void writeDef(Path root, String stem, String yaml) throws IOException {
        Path file = root.resolve("reforges/" + stem + ".yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    // ── Folia-safe staging primitives (the BatCloudSuite idioms) ───────────────────────────────────

    /** teleportAsync {@code who} to {@code to} from its own scheduler, continuing after a settle tick. */
    private static void place(Entity who, Location to, Runnable then) {
        Scheduling.onEntity(who, () ->
                who.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(who, 2L, then)));
    }

    /**
     * Poll each game tick until {@code cond} holds or {@code maxTicks} elapse; {@code done} always runs on
     * {@code entity}'s scheduler (the first poll may be on the caller's thread after a cross-region hop).
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

    private void failAll(Harness h, String message) {
        for (String check : List.of(
                "reforge.blink.landsShortOfAWall", "reforge.blink.zeroBlinkAgainstPointBlankWall",
                "reforge.blink.neverPhasesIntoTerrain", "reforge.grapple.reelsTheEntityInSight",
                "reforge.grapple.zipsToTerrainWhenNoEntity", "reforge.grapple.wallShieldedEntityIsNotReeled",
                "reforge.singularity.beamPicksTheSightedBlock", "reforge.singularity.pullsVictimsInward",
                "reforge.singularity.implodesWithFalloffAndAttribution", "reforge.singularity.pullsTheCasterToo",
                "reforge.castling.swapCompletesWithVelocitiesZeroed", "reforge.castling.losBreakAborts",
                "reforge.castling.countdownCuesAreAudible", "reforge.castling.cooldownStaysSpentOnAbort",
                "reforge.javelin.travelsAtAuthoredSpeedAndMisses", "reforge.javelin.hitsFirstVictimWithSwingDamage",
                "reforge.javelin.knockbackReversedAlongFlight", "reforge.javelin.victimHeldForLockTicks",
                "reforge.javelin.wallStopsTheFlight", "reforge.crossregion.attributionDegradesOffRegion")) {
            h.expect(check);
            h.fail(check, message);
        }
    }

    /** The wiring shared by every scenario (built once in {@link #accept}). */
    private record Deps(TriggerDispatch dispatch, ContentHolder holder, RegistryResolvers resolvers,
                        Messages messages) {
    }
}
