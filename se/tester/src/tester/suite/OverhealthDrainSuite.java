package tester.suite;

import engine.sink.ModernDispatchSink;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * Overhealth drain + potion lock, live (ADR-0057): the two just-changed {@link engine.sink.Sink} methods proven
 * against a real booted server across the modern matrix (Paper 1.17.1 → 26.1.x + Folia), over a clientless fake
 * player whose {@code GENERIC_MAX_HEALTH} attribute and potion effects are real server-side state.
 *
 * <ul>
 *   <li><strong>A — {@code drainMaxHealth} removes HEALTH_BOOST-potion overhealth.</strong> A real HEALTH_BOOST
 *       potion raises {@code getAttribute(GENERIC_MAX_HEALTH).getValue()} (a potion-sourced attribute MODIFIER,
 *       not a base shift); a fraction-1.0 drain must lower the EFFECTIVE cap back to the baseline for the window
 *       (a temporary counter-modifier), then restore the boosted cap after it.</li>
 *   <li><strong>B — {@code drainMaxHealth} removes a NAMED max-health MODIFIER's overhealth.</strong> The KEY
 *       regression the old base-only read missed (grim's trade / cupid's Lovestruck "removed overhealth" but the
 *       santa-hat / worn-HEALTH modifier hearts stayed): a FOREIGN {@code +hearts} {@link AttributeModifier} —
 *       distinct identity from the drain's own — is added directly to the attribute; the drain must still see it
 *       through the effective max and take it to the baseline, restoring after the window.</li>
 *   <li><strong>C — {@code potionLock} DENIES a re-application.</strong> With the type locked, an
 *       {@link Player#addPotionEffect} during the window must not stick (the modern {@link feature.combat.PotionLockListener}
 *       cancels the {@code EntityPotionEffectEvent}, backstopped by the per-tick re-strip); after the window a fresh
 *       application must stick again (the counter-proof that the denial was the lock, not a vacuous non-application).</li>
 * </ul>
 *
 * <p><strong>Era split.</strong> This is a modern-build suite (it drives {@link ModernDispatchSink} +
 * {@link RuntimeHandles}, both absent on 1.8.9). The 1.8.9 lane is a SEPARATE build (tester/build.gradle.kts
 * {@code -Pse.target=legacy}) driven by {@code LegacySmokePlugin}; the same two contracts over the {@code v1_8_R3}
 * NMS modifier path ({@code LegacyDispatchSink.addMaxHealthModifier}) and the re-strip-only lock backstop are pinned
 * there in {@code LegacySmokeSuite} — the modern suites do not compile against craftbukkit-1.8.8.
 *
 * <p>Fresh actor + fresh {@link CombatRig} per scenario, teardown after the last (temporally-separated) assertion;
 * server-side state only (fake players are clientless); every wait is GAME-TICK anchored.
 */
public final class OverhealthDrainSuite implements Harness.Scenario {

    /** The vanilla max-health baseline the drain measures overhealth above (20 = ten hearts). */
    private static final double BASELINE = 20.0;
    /** HEALTH_BOOST amplifier 4 = level 5 = +20 max HP → an effective 40 (a clear potion-sourced overhealth). */
    private static final int HEALTH_BOOST_AMP = 4;
    /** A long HEALTH_BOOST duration so the potion outlasts the whole drain window (its restore reads the boosted cap). */
    private static final int HEALTH_BOOST_DURATION = 100_000;
    /** The named-modifier scenario's bonus: +10 max HP (+5 hearts) → an effective 30. */
    private static final double MODIFIER_BONUS = 10.0;
    /** The drain window; the counter-modifier is removed this many ticks after the drain applies. */
    private static final int DRAIN_TICKS = 40;
    /** The potion-lock window; the re-strip backstop self-cancels this many ticks after the lock applies. */
    private static final int LOCK_TICKS = 40;
    /** Ticks to poll for a granted boost to register on the attribute before giving up. */
    private static final int GRANT_BUDGET = 40;
    /** Sample the drained cap this many ticks after flush (well inside the window; after the deferred drain lands). */
    private static final int DURING_DELAY = 8;
    /** Sample the restored cap this many ticks past the window's end. */
    private static final int RESTORE_MARGIN = 20;
    /** Re-apply the locked potion this many ticks after the lock applies (inside the window). */
    private static final int LOCK_APPLY_DELAY = 6;
    /** Ticks to let the cancel / re-strip settle before reading {@code hasPotionEffect}. */
    private static final int SETTLE = 4;
    /** The duration of a re-applied HEALTH_BOOST probe (long enough to still be present a few ticks later). */
    private static final int REAPPLY_DURATION = 200;
    /** Effective-cap comparison tolerance (attribute maths is exact; this absorbs only double noise). */
    private static final double EPS = 1.0e-3;

    private final Plugin plugin;

    public OverhealthDrainSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        // A retained resolver so the compile-interned potion id and the runtime handle pair (as CombatSuite).
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        PotionEffectType healthBoost =
                (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT, "HEALTH_BOOST");
        // The int id the Sink expects: interned through the SAME resolver, resolved back by the Sink via
        // DispatchSinkBase.potionEffect(int) → handles.potionEffect(id). (Direct-Sink route, as SinkSuite does.)
        OptionalInt healthBoostId = resolvers.potionEffect("HEALTH_BOOST");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        scenarioPotionBoost(h, handles, world, at, healthBoost);
        scenarioNamedModifier(h, handles, world, at);
        scenarioPotionLock(h, handles, world, at, healthBoost, healthBoostId);
    }

    // ── A: drain removes HEALTH_BOOST-potion overhealth to the baseline, then restores it ──────────

    private void scenarioPotionBoost(Harness h, RuntimeHandles handles, World world, Location at,
                                     PotionEffectType healthBoost) {
        final String during = "overhealth.drain.potionBoost.during";
        final String restored = "overhealth.drain.potionBoost.restored";
        h.expect(during);
        h.expect(restored);
        if (healthBoost == null) {
            failBoth(h, during, restored, "HEALTH_BOOST did not resolve on this version");
            return;
        }

        CombatRig rig = new CombatRig(plugin);
        rig.onArena(at, () -> {
            Player player;
            try {
                player = rig.spawnFake(world, "se_ovh_pot");
            } catch (Throwable t) {
                failBoth(h, during, restored, "fake-player spawn: " + t);
                rig.teardown();
                return;
            }
            Scheduling.onEntity(player, () -> {
                player.addPotionEffect(new PotionEffect(healthBoost, HEALTH_BOOST_DURATION, HEALTH_BOOST_AMP));
                // The HEALTH_BOOST modifier lands via the effect system, not synchronously — poll (tick-anchored).
                awaitUntil(player, () -> Stores.maxHealthOf(player) > BASELINE + EPS, 0, GRANT_BUDGET, rose -> {
                    if (!rose) {
                        failBoth(h, during, restored,
                                "HEALTH_BOOST did not raise effective max above " + BASELINE);
                        rig.teardown();
                        return;
                    }
                    double boosted = Stores.maxHealthOf(player);
                    ModernDispatchSink sink = newSink(handles);
                    sink.drainMaxHealth(player, 1.0, BASELINE, 0.0, DRAIN_TICKS);
                    sink.flush();
                    driveDrainWindow(h, rig, player, during, restored, boosted, "potion");
                });
            });
        });
    }

    // ── B: drain removes a NAMED max-health MODIFIER's overhealth (the santa-hat / worn-HEALTH case) ─

    private void scenarioNamedModifier(Harness h, RuntimeHandles handles, World world, Location at) {
        final String during = "overhealth.drain.namedModifier.during";
        final String restored = "overhealth.drain.namedModifier.restored";
        h.expect(during);
        h.expect(restored);

        CombatRig rig = new CombatRig(plugin);
        rig.onArena(at, () -> {
            Player player;
            try {
                player = rig.spawnFake(world, "se_ovh_mod");
            } catch (Throwable t) {
                failBoth(h, during, restored, "fake-player spawn: " + t);
                rig.teardown();
                return;
            }
            Scheduling.onEntity(player, () -> {
                double boosted = grantMaxHealthModifier(player, MODIFIER_BONUS);
                if (boosted <= BASELINE + EPS) {
                    failBoth(h, during, restored,
                            "could not add a +hearts max-health modifier (GENERIC_MAX_HEALTH unresolved?)");
                    rig.teardown();
                    return;
                }
                ModernDispatchSink sink = newSink(handles);
                sink.drainMaxHealth(player, 1.0, BASELINE, 0.0, DRAIN_TICKS);
                sink.flush();
                driveDrainWindow(h, rig, player, during, restored, boosted, "named-modifier");
            });
        });
    }

    /**
     * Shared drain-window driver: assert the effective cap fell to {@link #BASELINE} for the window (a
     * counter-modifier the size of the overhealth), then rose back to {@code boosted} once the window elapses
     * (the counter-modifier reverted). {@code boosted} is captured on the entity thread before the drain.
     */
    private void driveDrainWindow(Harness h, CombatRig rig, Player player,
                                  String during, String restored, double boosted, String source) {
        Scheduling.onEntityLater(player, DURING_DELAY, () -> h.guard(during, () -> {
            if (boosted <= BASELINE + EPS) {
                throw new IllegalStateException("no " + source + " overhealth to drain: boosted=" + boosted);
            }
            double now = Stores.maxHealthOf(player);
            if (Math.abs(now - BASELINE) > EPS) {
                throw new IllegalStateException("drain did not remove " + source + " overhealth to " + BASELINE
                        + "; effective=" + now + " (boosted was " + boosted + ")");
            }
        }));
        Scheduling.onEntityLater(player, DRAIN_TICKS + RESTORE_MARGIN, () -> {
            h.guard(restored, () -> {
                double now = Stores.maxHealthOf(player);
                if (Math.abs(now - boosted) > EPS) {
                    throw new IllegalStateException("drain window did not restore the " + source + " max " + boosted
                            + "; effective=" + now);
                }
            });
            rig.teardown();
        });
    }

    // ── C: potion lock DENIES a re-application during the window; a fresh one sticks after ─────────

    private void scenarioPotionLock(Harness h, RuntimeHandles handles, World world, Location at,
                                    PotionEffectType healthBoost, OptionalInt healthBoostId) {
        final String denied = "overhealth.potionLock.denied";
        final String reallowed = "overhealth.potionLock.reallowed";
        h.expect(denied);
        h.expect(reallowed);
        if (healthBoost == null || healthBoostId.isEmpty()) {
            failBoth(h, denied, reallowed, "HEALTH_BOOST id/type did not resolve on this version");
            return;
        }
        int id = healthBoostId.getAsInt();

        CombatRig rig = new CombatRig(plugin);
        // The modern EntityPotionEffectEvent guard (overlay/modern) — its cancel-on-reapply is the genuine
        // prevention; registered here so this suite exercises it (the per-tick re-strip is the 1.8.9 backstop).
        rig.listen(new feature.combat.PotionLockListener());
        rig.onArena(at, () -> {
            Player player;
            try {
                player = rig.spawnFake(world, "se_ovh_lock");
            } catch (Throwable t) {
                failBoth(h, denied, reallowed, "fake-player spawn: " + t);
                rig.teardown();
                return;
            }
            Scheduling.onEntity(player, () -> {
                ModernDispatchSink sink = newSink(handles);
                sink.potionLock(player, id, LOCK_TICKS);
                sink.flush();
                // During the window: a re-application must be denied (cancelled / re-stripped away).
                Scheduling.onEntityLater(player, LOCK_APPLY_DELAY, () -> {
                    player.addPotionEffect(new PotionEffect(healthBoost, REAPPLY_DURATION, 0));
                    Scheduling.onEntityLater(player, SETTLE, () -> {
                        h.guard(denied, () -> {
                            if (player.hasPotionEffect(healthBoost)) {
                                throw new IllegalStateException(
                                        "a locked HEALTH_BOOST re-application was NOT denied during the window");
                            }
                        });
                        // Past the window (lock ended, LockedPotions wall-window elapsed): a fresh one must stick.
                        Scheduling.onEntityLater(player, LOCK_TICKS, () -> {
                            player.addPotionEffect(new PotionEffect(healthBoost, REAPPLY_DURATION, 0));
                            Scheduling.onEntityLater(player, SETTLE, () -> {
                                h.guard(reallowed, () -> {
                                    if (!player.hasPotionEffect(healthBoost)) {
                                        throw new IllegalStateException(
                                                "HEALTH_BOOST did not stick after the lock window elapsed");
                                    }
                                });
                                rig.teardown();
                            });
                        });
                    });
                });
            });
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /** A fresh one-per-event {@link ModernDispatchSink} over an isolated env (mirrors SinkSuite/CombatSuite). */
    private static ModernDispatchSink newSink(RuntimeHandles handles) {
        return new ModernDispatchSink(handles, engine.sink.SinkEnv.of(
                platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                engine.stores.EngineStores.fresh(), () -> 0L));
    }

    /**
     * Add a FOREIGN {@code +bonus} max-health {@link AttributeModifier} (random identity, never the worn or drain
     * identity) directly to {@code GENERIC_MAX_HEALTH} and return the new effective max ({@link Stores#maxHealthOf}),
     * or {@code 0} if the attribute is absent. Resolves the attribute the same way {@code Stores.maxHealthOf}
     * reads it, and builds the modifier with the {@code (UUID, name, amount, Operation)} ctor — the cross-version
     * way (deprecated-not-removed, present on the 1.17.1 floor and at the 26.1.x ceiling, exactly as
     * {@code ModernDispatchSink.addMaxHealthModifier} / {@code ModernVanillaStats} build it).
     */
    @SuppressWarnings("deprecation") // AttributeModifier(UUID,String,double,Operation) + GENERIC_MAX_HEALTH: floor→ceiling, as production/Stores use
    private static double grantMaxHealthModifier(LivingEntity entity, double bonus) {
        AttributeInstance instance = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (instance == null) {
            return 0.0;
        }
        instance.addModifier(new AttributeModifier(UUID.randomUUID(), "starenchants.test.overhealth", bonus,
                AttributeModifier.Operation.ADD_NUMBER));
        return Stores.maxHealthOf(entity);
    }

    /** Poll {@code entity} each tick until {@code cond} holds ({@code done(true)}) or {@code maxTicks} elapse
     *  ({@code done(false)}). Tick-anchored (correct under matrix load); the condition reads on the entity thread. */
    private static void awaitUntil(Player entity, BooleanSupplier cond, int tick, int maxTicks,
                                   Consumer<Boolean> done) {
        if (cond.getAsBoolean()) {
            done.accept(true);
            return;
        }
        if (tick >= maxTicks) {
            done.accept(false);
            return;
        }
        Scheduling.onEntityLater(entity, 1L, () -> awaitUntil(entity, cond, tick + 1, maxTicks, done));
    }

    private static void failBoth(Harness h, String first, String second, String reason) {
        h.fail(first, reason);
        h.fail(second, reason);
    }
}
