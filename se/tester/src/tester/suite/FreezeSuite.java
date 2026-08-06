package tester.suite;

import engine.sink.FreezeLock;
import engine.sink.ModernDispatchSink;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import engine.sink.FrozenTargets;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * Ice Aspect frozen windows, live (FREEZE, ADR-0065): the {@link engine.sink.Sink#freeze} intent proven against
 * a real booted server across the modern matrix (Paper 1.17.1 → 26.1.x + Folia). Drives {@link ModernDispatchSink}
 * directly (as OverhealthDrainSuite), reads only server-side state (fake players are clientless), and anchors
 * every wait to GAME TICKS. Each check runs on its own {@link CombatRig} (isolated listeners + teardown); event
 * captors filter by the exact victim UUID so concurrently-launched checks never contaminate one another.
 *
 * <p>Every mob-victim check registers the production {@code FreezeDamageGuardListener} on its rig and pins the
 * victim with {@code setAI(false)} — the two production conditions the DoT assertions depend on. Without the
 * guard, the pinned victim's vanilla fully-frozen self-hurt (1.0/40t, NOT behind the freeze lock) arms real
 * i-frames, and on ≤1.20.6 the next DoT then fires as a partial whose event base is the REDUCED
 * {@code amount − lastHurt} (raw 1.0, not 2.0). Without the pin, a panicking cow wanders off the force-loaded
 * (entity-ticking) arena chunk into a border chunk where Folia still runs entity-scheduler tasks but
 * {@code invulnerableTime} (baseTick-only decay) freezes &gt;10 — an equal 2.0 DoT hurt is then dropped with
 * no event at all (both javap-verified on Folia 1.20.6; production victims sit next to a real, chunk-ticking
 * attacker and always have the guard).
 *
 * <ul>
 *   <li><strong>1 — the pin holds while the victim burns.</strong> The owner-mandated coexistence check: on the
 *       lock path (1.18.2+, all Folia) a burning cow stays pinned at max freeze ticks; on the 1.17.1 floor the
 *       visual honestly drops to 0 while burning (pin skipped). Both the vanilla fire tick and the attributed
 *       engine DoT land.</li>
 *   <li><strong>2 — DoT cadence + attribution.</strong> Exactly 3 attributed hits at t+20/40/60 — the chain runs in
 *       tick space (ADR-0065), so the t+duration boundary slot lands and wall/tick drift never adds a straggler.</li>
 *   <li><strong>3 — the slow's lifecycle.</strong> Both MOVEMENT_SPEED modifiers present during the window, gone
 *       after it, with the freeze thawed + unlocked.</li>
 *   <li><strong>4 — a re-proc REFRESHES, never stacks.</strong> One DoT chain across the extended window: a mid-period
 *       re-proc extends the tick budget from the last completed slot, so exactly 4 hits land (t+80 included).</li>
 *   <li><strong>5 — vanilla freeze self-damage is cancelled</strong> inside a window by the guard, but not on a
 *       non-frozen victim.</li>
 * </ul>
 *
 * <p>Modern-build suite (the 1.8.9 lane is a no-op visual; the legacy smoke's compile gate covers the leaf).
 */
public final class FreezeSuite implements Harness.Scenario {

    private static final double EPS = 1.0e-6;

    private final Plugin plugin;

    public FreezeSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);
        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        checkPinHoldsWhileBurning(h, handles, world, at);
        checkDotCadence(h, handles, world, at);
        checkSlowLifecycle(h, handles, world, at);
        checkRefreshNotStack(h, handles, world, at);
        checkSelfDamageCancelled(h, handles, world, at);
    }

    // ── 1: the pinned visual coexists with fire (lock path) / honestly drops (floor); both DoTs land ──

    private void checkPinHoldsWhileBurning(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "freeze.pin.holdsWhileBurning";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new feature.combat.FreezeDamageGuardListener()); // production parity: vanilla freeze self-hurts stay cancelled
        rig.onArena(at, () -> {
            Player attacker;
            LivingEntity cow;
            try {
                attacker = rig.spawnFake(world, "se_ice_burn_a");
                cow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = cow.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger fireEvents = new AtomicInteger();
            AtomicInteger engineTicks = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageEvent e) {
                    if (!e.getEntity().getUniqueId().equals(victimId)) {
                        return;
                    }
                    EntityDamageEvent.DamageCause cause = e.getCause();
                    if (cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.FIRE) {
                        fireEvents.incrementAndGet();
                    }
                }

                @EventHandler(priority = EventPriority.MONITOR)
                public void onAttributed(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(attackerId)) {
                        engineTicks.incrementAndGet();
                    }
                }
            });
            Scheduling.onEntity(cow, () -> {
                toughen(handles, cow); // survive the whole observation window so no DoT is lost to death
                cow.setAI(false); // a panicking wander off the entity-ticking arena chunk freezes its i-frames (class doc)
                cow.setNoDamageTicks(0);
                cow.setFireTicks(200);
                ModernDispatchSink sink = newSink(handles);
                sink.freeze(cow, 100, 2.0, 20, 5.0, true, 0, false, attacker);
                sink.flush();
                Scheduling.onEntityLater(cow, 8L, () -> {
                    boolean lockPath = FreezeLock.available();
                    int max = cow.getMaxFreezeTicks();
                    int freeze0 = cow.getFreezeTicks();
                    int fire0 = cow.getFireTicks();
                    Scheduling.onEntityLater(cow, 1L, () -> {
                        int freeze1 = cow.getFreezeTicks();
                        int fire1 = cow.getFireTicks();
                        Scheduling.onEntityLater(cow, 60L, () -> {
                            h.guard(key, () -> {
                                if (lockPath) {
                                    if (freeze0 < max || freeze1 < max) {
                                        throw new IllegalStateException("lock path: freeze not pinned at max while"
                                                + " burning; f0=" + freeze0 + " f1=" + freeze1 + " max=" + max);
                                    }
                                    if (fire0 <= 0 || fire1 <= 0) {
                                        throw new IllegalStateException("victim was not burning during the pin;"
                                                + " fire0=" + fire0 + " fire1=" + fire1);
                                    }
                                } else if (freeze0 != 0 || freeze1 != 0) {
                                    throw new IllegalStateException("floor: a burning victim's freeze must drop to 0"
                                            + " (pin skipped); f0=" + freeze0 + " f1=" + freeze1);
                                }
                                if (fireEvents.get() < 1) {
                                    throw new IllegalStateException("no vanilla fire tick observed while burning");
                                }
                                if (engineTicks.get() < 2) {
                                    throw new IllegalStateException("fewer than 2 attributed engine DoT ticks: "
                                            + engineTicks.get());
                                }
                            });
                            rig.teardown();
                        });
                    });
                });
            });
        });
    }

    // ── 2: exactly 3 attributed DoT ticks at t+20/40/60, each raw 2.0 ─────────────────────────────

    private void checkDotCadence(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "freeze.dot.cadenceAndAttribution";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        // Production parity: unguarded, the pinned cow's vanilla self-hurt (1.0 at tickCount%40) arms i-frames
        // 1-9t before a slot on Folia (+1-tick hops), and ≤1.20.6 fires that slot's event at raw 2.0−1.0.
        rig.listen(new feature.combat.FreezeDamageGuardListener());
        rig.onArena(at, () -> {
            Player attacker;
            LivingEntity cow;
            try {
                attacker = rig.spawnFake(world, "se_ice_dot_a");
                cow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = cow.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger count = new AtomicInteger();
            AtomicInteger offCadence = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.LOWEST)
                public void onAttributed(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(attackerId)) {
                        count.incrementAndGet();
                        if (Math.abs(e.getDamage() - 2.0) > EPS) {
                            offCadence.incrementAndGet(); // raw, pre-reduction, must be exactly the authored 2.0
                        }
                    }
                }
            });
            Scheduling.onEntity(cow, () -> {
                toughen(handles, cow);
                cow.setAI(false); // a panicking wander off the entity-ticking arena chunk freezes its i-frames (class doc)
                cow.setNoDamageTicks(0);
                ModernDispatchSink sink = newSink(handles);
                // Duration 60 is a period-lattice multiple: the t+duration boundary slot is INCLUSIVE
                // (ADR-0065 tick-space cadence), so the hits land at exactly t+20/40/60 — no more, no less.
                sink.freeze(cow, 60, 2.0, 20, 0.0, true, 0, false, attacker);
                sink.flush();
                // Tick-anchored: await the expected count, then hold one-period-plus so a straggler slot
                // (the old wall-clock-drift bug landed one exactly a period late) would still be caught.
                awaitUntil(cow, () -> count.get() >= 3, 0, 120, reached ->
                        Scheduling.onEntityLater(cow, 25L, () -> {
                            h.guard(key, () -> {
                                if (count.get() != 3) {
                                    throw new IllegalStateException("expected exactly 3 attributed DoT ticks"
                                            + " (t+20/40/60 — the t+duration boundary slot lands), got " + count.get());
                                }
                                if (offCadence.get() != 0) {
                                    throw new IllegalStateException(offCadence.get() + " DoT tick(s) were not raw 2.0 at LOWEST");
                                }
                            });
                            rig.teardown();
                        }));
            });
        });
    }

    // ── 3: the slow + frost-offset modifiers appear during the window and are stripped after it ────

    private void checkSlowLifecycle(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "freeze.slow.appliedAndRemoved";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(at, () -> {
            Player p;
            try {
                p = rig.spawnFake(world, "se_ice_slow_p");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            // No damage is staged, so spawn-invulnerability is irrelevant; still settle per the tester-harness fact.
            Scheduling.onEntityLater(p, 70L, () -> {
                ModernDispatchSink sink = newSink(handles);
                sink.freeze(p, 40, 0.0, 20, 5.0, true, 0, false, null);
                sink.flush();
                Scheduling.onEntityLater(p, 10L, () -> {
                    boolean slowNow = hasModifierNamed(handles, p, "starenchants.frozen_slow");
                    boolean offsetNow = hasModifierNamed(handles, p, "starenchants.frozen_frost_offset");
                    // Teardown lands at the window's final lattice slot (t+40); poll tick-by-tick so the
                    // wait stays anchored to the chain itself rather than a parallel fixed delay.
                    awaitUntil(p, () -> !hasModifierNamed(handles, p, "starenchants.frozen_slow")
                                    && !hasModifierNamed(handles, p, "starenchants.frozen_frost_offset"),
                            0, 120, removed -> {
                                h.guard(key, () -> {
                                    if (!slowNow) {
                                        throw new IllegalStateException("frozen_slow modifier missing during the window");
                                    }
                                    if (!offsetNow) {
                                        throw new IllegalStateException("frozen_frost_offset modifier missing during the window");
                                    }
                                    if (!removed) {
                                        throw new IllegalStateException("frozen-slow modifiers were not removed after the window");
                                    }
                                    if (p.getFreezeTicks() != 0) {
                                        throw new IllegalStateException("freeze ticks not zeroed after the window: "
                                                + p.getFreezeTicks());
                                    }
                                    if (FreezeLock.isLocked(p)) {
                                        throw new IllegalStateException("freeze lock not released after the window");
                                    }
                                });
                                rig.teardown();
                            });
                });
            });
        });
    }

    // ── 4: a mid-period re-proc (t+30) refreshes the live window (one DoT chain), never a second ──

    private void checkRefreshNotStack(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "freeze.reproc.refreshNotStack";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new feature.combat.FreezeDamageGuardListener()); // production parity: vanilla freeze self-hurts stay cancelled
        rig.onArena(at, () -> {
            Player attacker;
            LivingEntity cow;
            try {
                attacker = rig.spawnFake(world, "se_ice_reproc_a");
                cow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = cow.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger count = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.LOWEST)
                public void onAttributed(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(attackerId)) {
                        count.incrementAndGet();
                    }
                }
            });
            Scheduling.onEntity(cow, () -> {
                toughen(handles, cow);
                cow.setAI(false); // a panicking wander off the entity-ticking arena chunk freezes its i-frames (class doc)
                cow.setNoDamageTicks(0);
                ModernDispatchSink sink1 = newSink(handles);
                sink1.freeze(cow, 40, 2.0, 20, 0.0, true, 0, false, attacker);
                sink1.flush();
                // Re-proc MID-PERIOD (t+30): the refresh anchors at the chain's last completed slot (t+20),
                // extending the tick budget to exactly t+80. A re-proc ON a lattice tick would race the
                // chain's same-tick run (either order is a legal window), so mid-period is the
                // deterministic staging point.
                Scheduling.onEntityLater(cow, 30L, () -> {
                    ModernDispatchSink sink2 = newSink(handles);
                    sink2.freeze(cow, 60, 2.0, 20, 0.0, true, 0, false, attacker); // extends the window; must not stack a chain
                    sink2.flush();
                    // Tick-anchored: await the expected count, then hold one-period-plus — a stacked second
                    // chain or a wall-drift straggler lands within one period of the last legitimate slot.
                    awaitUntil(cow, () -> count.get() >= 4, 0, 120, reached ->
                            Scheduling.onEntityLater(cow, 25L, () -> {
                                h.guard(key, () -> {
                                    if (count.get() != 4) {
                                        throw new IllegalStateException("refresh-not-stack: expected 4 DoT ticks on"
                                                + " ONE chain (t+20/40/60/80 — the t+30 re-proc extends the budget"
                                                + " to t+80), got " + count.get()
                                                + " (a stacked second chain would double a period)");
                                    }
                                });
                                rig.teardown();
                            }));
                });
            });
        });
    }

    // ── 5: the guard cancels vanilla FREEZE self-damage inside a window, but spares a non-frozen victim ──

    private void checkSelfDamageCancelled(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "freeze.vanillaSelfDamage.cancelled";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new feature.combat.FreezeDamageGuardListener()); // the production guard under test
        rig.onArena(at, () -> {
            LivingEntity frozen;
            LivingEntity control;
            try {
                frozen = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                control = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            Scheduling.onEntity(frozen, () -> {
                ModernDispatchSink sink = newSink(handles);
                sink.freeze(frozen, 60, 0.0, 20, 0.0, true, 0, false, null);
                sink.flush();
                UUID frozenId = frozen.getUniqueId();
                // Poll until the freeze WINDOW is actually registered (the guard cancels off the SAME
                // FrozenTargets.isFrozen read), not a fixed 5-tick wait: under CI load the arm map-write
                // lands later than 5 ticks and the guard sees a not-yet-frozen victim — the flake.
                awaitUntil(frozen, () -> FrozenTargets.isFrozen(frozenId), 0, 60, armed -> {
                    h.guard(key, () -> {
                        if (!armed) {
                            throw new IllegalStateException("the freeze window never registered within 60 ticks");
                        }
                        boolean cancelledOnFrozen = callFreezeDamage(frozen);
                        boolean cancelledOnControl = callFreezeDamage(control); // same region — region-correct
                        if (!cancelledOnFrozen) {
                            throw new IllegalStateException("vanilla FREEZE self-damage was NOT cancelled on a frozen victim");
                        }
                        if (cancelledOnControl) {
                            throw new IllegalStateException("FREEZE damage was cancelled on a NON-frozen victim (guard too broad)");
                        }
                    });
                    rig.teardown();
                });
            });
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /** A fresh one-per-event {@link ModernDispatchSink} over an isolated env (mirrors OverhealthDrainSuite). */
    private static ModernDispatchSink newSink(RuntimeHandles handles) {
        return new ModernDispatchSink(handles, engine.sink.SinkEnv.of(
                platform.economy.EconomyService.NONE, engine.sink.SoulDebit.NONE,
                engine.stores.EngineStores.fresh(), () -> 0L));
    }

    /** Raise the victim's max health so a whole observation window of DoT never truncates on death. */
    private static void toughen(RuntimeHandles handles, LivingEntity entity) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MAX_HEALTH");
        if (attribute instanceof Attribute resolved) {
            AttributeInstance instance = entity.getAttribute(resolved);
            if (instance != null) {
                instance.setBaseValue(1000.0);
                entity.setHealth(1000.0);
            }
        }
    }

    /** Whether the MOVEMENT_SPEED attribute carries a modifier of the given wire name (resolve as production does). */
    private static boolean hasModifierNamed(RuntimeHandles handles, LivingEntity entity, String name) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MOVEMENT_SPEED");
        if (!(attribute instanceof Attribute resolved)) {
            return false;
        }
        AttributeInstance instance = entity.getAttribute(resolved);
        if (instance == null) {
            return false;
        }
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (name.equals(modifierName(modifier))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation") // getName(): the modifier's wire identity, deprecated-not-removed across the range
    private static String modifierName(AttributeModifier modifier) {
        return modifier.getName();
    }

    @SuppressWarnings("deprecation") // EntityDamageEvent(Entity, DamageCause, double): deprecated-not-removed across the modern range (javap-verified floor→ceiling)
    private boolean callFreezeDamage(LivingEntity victim) {
        EntityDamageEvent event = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FREEZE, 1.0);
        plugin.getServer().getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /** Poll {@code entity} each tick until {@code cond} holds ({@code done(true)}) or {@code maxTicks} elapse
     *  ({@code done(false)}). Tick-anchored (correct under matrix load); the condition reads on the entity thread. */
    private static void awaitUntil(Entity entity, BooleanSupplier cond, int tick, int maxTicks, Consumer<Boolean> done) {
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
}
