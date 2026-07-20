package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Lang;
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
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.reforge.CastlingService;
import feature.reforge.GravityWellService;
import feature.reforge.GrappleService;
import feature.reforge.JavelinService;
import feature.reforge.ReforgeMachines;
import feature.reforge.ReforgeMessenger;
import feature.reforge.ReforgeRunner;
import feature.reforge.ReforgeUseListener;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import platform.economy.EconomyService;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The reforge active driven the way a REAL player fires it: a stamped weapon in the main hand, SHIFT +
 * right-click synthesized as a {@link PlayerInteractEvent}, and the full production chain —
 * {@link ReforgeUseListener} &rarr; {@link ReforgeRunner} &rarr; {@link ReforgeMachines} &rarr; the services —
 * over the REAL era raycasts. The v1.10.0 regression class this pins: every other reforge suite bypassed that
 * chain (direct {@code fireUse}/{@code service.start} with stubbed rays and pitch-0 floating casters), so a
 * broken listener gate, machine routing, or real-ray semantics shipped matrix-green and died in real play.
 *
 * <p>Every scenario runs on its own {@link CombatRig} at its own elevated void arena, on a row
 * ({@code ARENA_BASE} 2048) the other reforge suites never touch — a genuinely distinct Folia region, so
 * fake players spawn on the world-spawn region thread and teleport in ({@link #spawnFakeAtSpawn}) while
 * blocks and cows stage on the arena's own region. Cows and casters are gravity-pinned; waits are
 * tick-anchored. Defs reuse the {@link ReforgeMotionSuite} authored numbers so felt-units match
 * the shipped pack. Modern-build suite (the 1.8.9 lane's compile gate covers the era leaves).
 */
public final class ReforgeUseSuite implements Harness.Scenario {

    /** Elevated void arenas, clear of terrain and the ground-level suites. */
    private static final int ARENA_Y = 200;
    /** Own row: X AND Z offset 2048 — ReforgeMotionSuite's row sits at 512, so the suites never collide. */
    private static final int ARENA_BASE = 2048;
    /** Between-scenario spacing — far past grapple range (14) / well radius (6). */
    private static final int ARENA_STEP = 96;
    private static final double POS_EPS = 0.40;

    // Arena indices — one distinct sky arena per check.
    private static final int A_BLINK_FIRE = 0;
    private static final int A_BLINK_PLAIN = 1;
    private static final int A_GRAPPLE_RAY = 2;
    private static final int A_GRAPPLE_AIR = 3;
    private static final int A_SING_SKY = 4;

    private static final String K_BLINK = "reforge.use.sneakRightClickFiresBlinkThroughTheListener";
    private static final String K_PLAIN = "reforge.use.nonSneakClickFallsThrough";
    private static final String K_REEL = "reforge.use.grappleReelsThroughRealRays";
    private static final String K_AIR = "reforge.use.grappleAirZipsAndArmsCooldown";
    private static final String K_SKY = "reforge.use.singularityAirAimDropsToGround";

    // ── Test defs — the ReforgeMotionSuite constants verbatim (same authored numbers) ──────────────

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

    /** Test-owned sentinel templates ({@link #countingMessages}) — the test owns both ends, no catalogue re-type. */
    private static final String WHIFF_MARK = "whiff-fragment-rendered";
    private static final String COOLDOWN_MARK = "cooldown-fragment-rendered";
    /** The runner speaks through config templates (the pets convention) — sentinel those too: silent
     *  activate/end lines keep the motion scenarios clean; the cooldown line is the countable mark. */
    private static final compile.load.MasterConfig.ReforgesSection SENTINEL_SECTION =
            new compile.load.MasterConfig.ReforgesSection(java.util.List.of("SWORD", "AXE"),
                    "", "", COOLDOWN_MARK, "", true);

    /** No javelin def here — the machines ctor still needs the seam. */
    private static final WeaponDamage WEAPON = p -> 6.0;

    private final Plugin plugin;

    public ReforgeUseSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-reforge-use-suite");
            writeDef(root, "leviathans-reach", LEVIATHAN);
            writeDef(root, "blink", BLINK);
            writeDef(root, "singularity", SINGULARITY);
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
        // Constant tick: a gate-6 cooldown reads its full remainder, so the air-zip scenario's immediate second
        // use is deterministically cooldown-gated (the ReforgeMotionSuite clock idiom).
        SinkEnv env = SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(), () -> 0L);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());

        Deps deps = new Deps(dispatch, holder, resolvers, combat, Messages.defaults());
        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();

        sneakRightClickFiresBlinkThroughTheListener(h, deps, world, spawn);
        nonSneakClickFallsThrough(h, deps, world, spawn);
        grappleReelsThroughRealRays(h, deps, world, spawn);
        grappleAirZipsAndArmsCooldown(h, deps, world, spawn);
        singularityAirAimDropsToGround(h, deps, world, spawn);
    }

    // ── The listener chain: SHIFT + right-click on a stamped weapon, exactly as production wires it ─

    private void sneakRightClickFiresBlinkThroughTheListener(Harness h, Deps deps, World world, Location spawn) {
        final String key = K_BLINK;
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_FIRE);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            // A real floor under the path + a grounded look-down: the real-play posture the v1.10 suites
            // never staged (their pitch-0 floating casters passed while every grounded blink zeroed).
            // Staged HERE — block writes belong to the arena's own region thread.
            for (int i = 0; i <= 6; i++) {
                solid(world, bx + i, by - 1, bz);
            }
            Location stand = centerFacing(arena, -90.0f); // yaw -90 → facing +X
            stand.setPitch(30.0f);
            spawnFakeAtSpawn(h, key, rig, world, "se_rfu_blk1", caster ->
                    place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                arm(deps, caster, "blink");
                ReforgeUseListener listener = chain(deps, deps.messages());
                boolean claimed = use(listener, caster, true);
                awaitUntil(caster, () -> forwardX(caster, stand) >= 3.0, 0, 30, moved -> {
                    h.guard(key, () -> {
                        if (!claimed) {
                            throw new IllegalStateException("the sneak right-click was not claimed "
                                    + "(useItemInHand != DENY)");
                        }
                        double dx = forwardX(caster, stand);
                        if (dx < 3.0) {
                            throw new IllegalStateException("the listener-driven blink moved " + dx
                                    + " blocks of the authored 4 (expected >= 3)");
                        }
                    });
                    rig.teardown();
                });
            })));
        });
    }

    private void nonSneakClickFallsThrough(Harness h, Deps deps, World world, Location spawn) {
        final String key = K_PLAIN;
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_BLINK_PLAIN);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            for (int i = 0; i <= 6; i++) {
                solid(world, bx + i, by - 1, bz);
            }
            Location stand = centerFacing(arena, -90.0f);
            stand.setPitch(30.0f);
            spawnFakeAtSpawn(h, key, rig, world, "se_rfu_blk2", caster ->
                    place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                arm(deps, caster, "blink"); // stamped and held — the fall-through is the SNEAK gate alone
                ReforgeUseListener listener = chain(deps, deps.messages());
                boolean claimed = use(listener, caster, false);
                Scheduling.onEntityLater(caster, 15L, () -> { h.guard(key, () -> {
                    if (claimed) {
                        throw new IllegalStateException("a non-sneak right-click was claimed (the weapon's "
                                + "vanilla use is lost)");
                    }
                    double dx = forwardX(caster, stand);
                    if (Math.abs(dx) > POS_EPS) {
                        throw new IllegalStateException("a non-sneak click still blinked the caster " + dx
                                + " blocks");
                    }
                }); rig.teardown(); });
            })));
        });
    }

    private void grappleReelsThroughRealRays(Harness h, Deps deps, World world, Location spawn) {
        final String key = K_REEL;
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_GRAPPLE_RAY);
        int bx = arena.getBlockX();
        int by = arena.getBlockY();
        int bz = arena.getBlockZ();
        rig.onArena(arena, () -> {
            // Cow + floor staged HERE — entity adds and block writes belong to the arena's own region thread.
            LivingEntity cow = staticCow(rig, world, arena);
            // One shared floor line under caster and cow — the real block ray must NOT ground in it.
            for (int i = -1; i <= 10; i++) {
                solid(world, bx + i, by - 1, bz);
            }
            Location stand = centerFacing(arena, -90.0f); // facing +X
            // Aim the EYE ray through the cow's torso, computed not guessed: eye 1.62 over feet, torso centre
            // ~0.7 up, 8 blocks out → atan2(0.92, 8) ≈ 6.6° down (still intersects under the sneak-pose eye).
            stand.setPitch((float) Math.toDegrees(Math.atan2(1.62 - 0.7, 8.0)));
            Location cowAt = stand.clone().add(8, 0, 0);  // dead-ahead on the facing line, same floor
            Location reelTo = stand.clone().add(2, 0, 0); // reel-distance 2 in front, feet Y kept
            spawnFakeAtSpawn(h, key, rig, world, "se_rfu_grp1", caster ->
                    place(caster, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                arm(deps, caster, "leviathans-reach");
                ReforgeUseListener listener = chain(deps, deps.messages());
                boolean claimed = use(listener, caster, true);
                awaitUntil(cow, () -> horiz(cow.getLocation(), reelTo) <= 1.0, 0, 30, reeled -> {
                    h.guard(key, () -> {
                        if (!claimed) {
                            throw new IllegalStateException("the sneak right-click was not claimed");
                        }
                        if (horiz(cow.getLocation(), reelTo) > 1.0) {
                            throw new IllegalStateException("the REAL entity ray did not acquire the cow "
                                    + "dead-ahead: " + horiz(cow.getLocation(), reelTo)
                                    + " blocks off the reel spot");
                        }
                    });
                    rig.teardown();
                });
            }))));
        });
    }

    private void grappleAirZipsAndArmsCooldown(Harness h, Deps deps, World world, Location spawn) {
        final String key = K_AIR;
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_GRAPPLE_AIR);
        rig.onArena(arena, () -> {
            Location stand = centerFacing(arena, -90.0f); // pitch 0 into open void: both real rays miss → air zip
            AtomicInteger whiffs = new AtomicInteger();
            AtomicInteger cooldowns = new AtomicInteger();
            spawnFakeAtSpawn(h, key, rig, world, "se_rfu_grp2", caster ->
                    place(caster, stand, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                arm(deps, caster, "leviathans-reach");
                ReforgeUseListener listener = chain(deps, countingMessages(caster, whiffs, cooldowns));
                use(listener, caster, true); // open-air use — grappling air ZIPS the caster (never a whiff), arms cooldown
                Scheduling.onEntityLater(caster, 2L, () -> {
                    use(listener, caster, true); // immediate re-use — gated by the armed (never-refunded) cooldown
                    // The zip is a launched velocity applied ~flightTicks (~7) after the emit — catch it the tick it lands.
                    awaitUntil(caster, () -> caster.getVelocity().getX() > 0.05, 0, 30, zipped -> {
                        h.guard(key, () -> {
                            if (caster.getVelocity().getX() <= 0.05) {
                                throw new IllegalStateException("grappling air did not zip the caster toward the "
                                        + "aimed point: v=" + caster.getVelocity());
                            }
                            if (whiffs.get() != 0) {
                                throw new IllegalStateException("air grapple rendered the removed whiff fragment "
                                        + whiffs.get() + " time(s) — it must ZIP now, never whiff");
                            }
                            if (cooldowns.get() != 1) {
                                throw new IllegalStateException("the second use rendered the cooldown fragment "
                                        + cooldowns.get() + " time(s), expected exactly once (the air zip must "
                                        + "have armed it — the never-refund economy)");
                            }
                        });
                        rig.teardown();
                    });
                });
            })));
        });
    }

    private void singularityAirAimDropsToGround(Harness h, Deps deps, World world, Location spawn) {
        final String key = K_SKY;
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        Location arena = arena(world, spawn, A_SING_SKY);
        rig.onArena(arena, () -> {
            // Aim pitch 0 into open sky: the REAL block ray finds nothing ahead, so the well DROPS straight down
            // from the ray's end (eye + dir×range = 12 along +X) to the first solid block and anchors above it.
            // Plant that ground block below eye level (the horizontal ray flies over it) at the ray-end column
            // (arena X+12, Z), so the drop target — and thus the core — is deterministic.
            int gx = arena.getBlockX() + 12;
            int gy = arena.getBlockY() - 3;
            int gz = arena.getBlockZ();
            solid(world, gx, gy, gz);
            LivingEntity cow = staticCow(rig, world, arena);
            Location stand = centerFacing(arena, -90.0f);
            // Ground core = ground-block centre + (0, 1 + rise(2.5), 0). Float the cow 3 east of it (inside radius
            // 6): a well that dropped to the ground drags it back in −X (the ReforgeMotionSuite §B1.15 velocity read).
            Location core = new Location(world, gx + 0.5, gy + 1.0 + 2.5, gz + 0.5);
            Location cowAt = core.clone().add(3, 0, 0);
            spawnFakeAtSpawn(h, key, rig, world, "se_rfu_sng1", caster -> {
                caster.setGravity(false); // keep the eye (the drop column's top) stable through activation
                place(caster, stand, () -> place(cow, cowAt, () -> Scheduling.onEntityLater(caster, 5L, () -> {
                    arm(deps, caster, "singularity");
                    ReforgeUseListener listener = chain(deps, deps.messages());
                    boolean claimed = use(listener, caster, true);
                    // The pull is a per-pulse toward-core VELOCITY; a NoAI cow never translates it, so the
                    // ground-dropped well is proven by the −X drag it imparts (the §B1.15 read).
                    awaitUntil(cow, () -> cow.getVelocity().getX() < -0.05, 0, 40, pulled -> {
                        h.guard(key, () -> {
                            if (!claimed) {
                                throw new IllegalStateException("the sneak right-click was not claimed");
                            }
                            if (cow.getVelocity().getX() >= -0.05) {
                                throw new IllegalStateException("an air aim never dropped the well to the ground: "
                                        + "cow pull velocity x=" + cow.getVelocity().getX()
                                        + " (expected < 0 toward the ground core)");
                            }
                        });
                        rig.teardown();
                    });
                })));
            });
        });
    }

    // ── The production chain, wired per scenario ───────────────────────────────────────────────────

    /** Listener → runner → machines → services over the REAL era rays — production's activation chain. */
    private ReforgeUseListener chain(Deps deps, Messages messages) {
        GravityWellService gravityWell = new GravityWellService(deps.dispatch(),
                ReforgeUseSuite::targetBlock, deps.resolvers());
        GrappleService grapple = new GrappleService(deps.dispatch(),
                ReforgeUseSuite::targetEntity, ReforgeUseSuite::targetBlock);
        CastlingService castling = new CastlingService(deps.dispatch(),
                ReforgeUseSuite::targetEntity, messages, deps.resolvers());
        JavelinService javelin = new JavelinService(deps.dispatch(), WEAPON, deps.resolvers(),
                () -> true, () -> true, (a, b) -> false);
        ReforgeMachines machines = new ReforgeMachines(deps.holder(), gravityWell, grapple, castling, javelin);
        ReforgeRunner runner = new ReforgeRunner(deps.holder(), deps.dispatch(),
                new ReforgeMessenger(messages, () -> SENTINEL_SECTION), machines,
                EngineStores.fresh(), () -> 0L); // no windowed kinds here — the ENDED reads never fire
        return new ReforgeUseListener(runner, deps.combat(), Stores.hands(), () -> true);
    }

    /**
     * Give the caster a REAL stamped weapon: the combat blob carries the reforge key — the
     * {@code ReforgeService.apply} write path reduced to the one codec write the listener's
     * {@code codec.read(held).reforgeKey()} gate reads.
     */
    private static void arm(Deps deps, Player caster, String stem) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        deps.combat().write(sword, deps.combat().read(sword).withReforge("reforges/" + stem));
        caster.getInventory().setItemInMainHand(sword);
    }

    /**
     * Right-click the held main hand through the real listener; {@code true} when the event was claimed.
     * A synthetic RIGHT_CLICK_AIR event is {@code isCancelled()==true} FROM CONSTRUCTION (no clicked block ⇒
     * useInteractedBlock DENY — the paper-api contract), so the claim is read off {@code useItemInHand()},
     * which only the listener's {@code setCancelled(true)} flips to DENY.
     */
    private static boolean use(ReforgeUseListener listener, Player caster, boolean sneak) {
        caster.setSneaking(sneak);
        PlayerInteractEvent event = new PlayerInteractEvent(caster, Action.RIGHT_CLICK_AIR,
                caster.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND);
        listener.onInteract(event);
        return event.useItemInHand() == Event.Result.DENY;
    }

    /**
     * The runner/services' Messages over a TEST-OWNED {@link Lang} fixture — sentinel templates for the two
     * keys under assertion (success stays production's silent empty) — with the ReforgeMotionSuite
     * counting-placeholders seam, so the count is key-level without re-typing any shipped catalogue string.
     */
    private static Messages countingMessages(Player caster, AtomicInteger whiffs, AtomicInteger cooldowns) {
        Lang lang = new Lang(Map.of(
                "reforge.grapple.whiff", WHIFF_MARK), Map.of(), List.of());
        return new Messages(() -> lang, () -> "", () -> true, (p, text) -> {
            if (p.getUniqueId().equals(caster.getUniqueId())) {
                if (WHIFF_MARK.equals(text)) {
                    whiffs.incrementAndGet();
                } else if (COOLDOWN_MARK.equals(text)) {
                    cooldowns.incrementAndGet();
                }
            }
            return text;
        });
    }

    // ── The REAL modern acquisition rays, inlined — keep in LOCKSTEP with
    // bootstrap/overlay/modern/bootstrap/compat/ModernTargets.java (the tester cannot depend on bootstrap) ──

    private static final double RAY_INFLATE = 0.3;

    private static Entity targetEntity(Player from, int maxDistance) {
        Location eye = from.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = maxDistance;
        RayTraceResult wall = from.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            range = Math.max(0.0, wall.getHitPosition().distance(eye.toVector()));
        }
        RayTraceResult hit = from.getWorld().rayTraceEntities(eye, dir, range, RAY_INFLATE,
                e -> e != from && e instanceof LivingEntity && !(e instanceof ArmorStand)
                        && !(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR));
        return hit == null ? null : hit.getHitEntity();
    }

    private static Block targetBlock(Player from, int maxDistance) {
        Location eye = from.getEyeLocation();
        RayTraceResult hit = from.getWorld().rayTraceBlocks(eye, eye.getDirection(), maxDistance,
                FluidCollisionMode.NEVER, true);
        return hit == null ? null : hit.getHitBlock();
    }

    // ── Staging + geometry helpers (the ReforgeMotionSuite idioms) ─────────────────────────────────

    /** A chunk-centred sky arena for scenario {@code index}: far from spawn and from every other scenario. */
    private static Location arena(World world, Location spawn, int index) {
        int rawX = spawn.getBlockX() + ARENA_BASE + index * ARENA_STEP;
        int rawZ = spawn.getBlockZ() + ARENA_BASE;
        int bx = (rawX & ~15) + 8; // chunk-mid so short-range staging stays in the one force-loaded chunk
        int bz = (rawZ & ~15) + 8;
        return new Location(world, bx, ARENA_Y, bz);
    }

    /** The arena centre as an entity stand point (block-centred) with the given yaw, level pitch. */
    private static Location centerFacing(Location arena, float yaw) {
        return new Location(arena.getWorld(), arena.getBlockX() + 0.5, arena.getBlockY(),
                arena.getBlockZ() + 0.5, yaw, 0.0f);
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

    /**
     * FakePlayers registers the new player into the WORLD-SPAWN chunk, which this suite's 2048-offset arena
     * region threads do NOT own on Folia ("Cannot add entity off-main thread") — so spawn on the spawn-owning
     * region thread, gravity-pin there, and hand the caster back for the {@code teleportAsync} hop into the
     * arena (the {@code crossRegionAttributionDegradesOffRegion} idiom; on single-threaded Paper the hop is a
     * no-op).
     */
    private void spawnFakeAtSpawn(Harness h, String key, CombatRig rig, World world, String name,
                                  Consumer<Player> then) {
        Scheduling.onRegion(world.getSpawnLocation(), () -> {
            Player caster;
            try {
                caster = rig.spawnFake(world, name);
            } catch (Throwable t) {
                h.fail(key, "fake-player spawn: " + t);
                rig.teardown();
                return;
            }
            caster.setGravity(false);
            then.accept(caster);
        });
    }

    private static double forwardX(Player p, Location from) {
        return p.getLocation().getX() - from.getX();
    }

    private static double horiz(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** teleportAsync {@code who} to {@code to} from its own scheduler, continuing after a settle tick. */
    private static void place(Entity who, Location to, Runnable then) {
        Scheduling.onEntity(who, () ->
                who.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(who, 2L, then)));
    }

    /**
     * Poll each game tick until {@code cond} holds or {@code maxTicks} elapse; {@code done} always runs on
     * {@code entity}'s scheduler.
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

    private static void writeDef(Path root, String stem, String yaml) throws IOException {
        Path file = root.resolve("reforges/" + stem + ".yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    private void failAll(Harness h, String message) {
        for (String check : List.of(K_BLINK, K_PLAIN, K_REEL, K_AIR, K_SKY)) {
            h.expect(check);
            h.fail(check, message);
        }
    }

    /** The wiring shared by every scenario (built once in {@link #accept}). */
    private record Deps(TriggerDispatch dispatch, ContentHolder holder, RegistryResolvers resolvers,
                        CombatCodec combat, Messages messages) {
    }
}
