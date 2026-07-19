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
import engine.sink.DotParkLedger;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.ComboDotRelease;
import feature.combat.ComboDotSyncListener;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.CrossRegion;
import tester.harness.Harness;

/**
 * Combo-DoT sync, live (ADR-0069): SE never breaks a Mental combo with its own damage-over-time. Mental is NOT
 * installed on the matrix servers, so {@code ComboStartEvent} never fires — the suite drives the park ledger
 * through its public combo-state seam ({@code comboStarted}/{@code comboEnded}, the same writes the reflective
 * bridge makes) and proves everything DOWNSTREAM against the REAL vanilla damage pipeline: real i-frame
 * windows, real kill decisions, real event counts — exactly the part the unit tests cannot reach. Each check
 * runs on its own {@link CombatRig} (isolated listeners + teardown) with fresh actors; captors filter by the
 * victim UUID so concurrently-launched checks never contaminate each other.
 *
 * <p>Victims are clientless fake players (parking only banks {@link Player} targets). They carry vanilla's
 * ~60-tick spawn invulnerability, so every hit-staging waits {@link #JOIN_PROTECTION_BUDGET} first (a hit
 * inside that window silently no-ops), then stages health so peaceful regen cannot skew a delta.
 *
 * <ul>
 *   <li><strong>parkWhileComboActive</strong> — a deferred {@code damage} and a freeze DoT chain, both aimed at
 *       a comboed victim, apply ZERO damage: no health lost, no {@link EntityDamageEvent}, the whole owed total
 *       banked (deferred 4.0 + four freeze slots × 2.0).</li>
 *   <li><strong>flushJoinsARealHit</strong> — the attacker's parked bucket folds into their next real hit: one
 *       event, committed == raw + parked, the bucket drained.</li>
 *   <li><strong>comboEndReleaseIsAttributedAndWindowAware</strong> — the combo-end release waits out an armed
 *       i-frame window, then lands exactly one attacker-attributed hit of the parked amount.</li>
 *   <li><strong>activeComboHoldsThirdPartyBuckets</strong> — a third party's bucket stays parked across the
 *       holder's real hit: no mid-combo release (which would switch Mental's tracker and end the combo).</li>
 *   <li><strong>cancelledHitReparks</strong> — a drained flush the event ends up cancelling is re-parked at
 *       MONITOR; the victim loses no health and the bucket survives.</li>
 *   <li><strong>clearMidReleaseAllowsAFreshBegin</strong> — a {@code clear} mid-release (the quit-sweep seam)
 *       drops the in-flight guard, so a fresh {@code begin} arms and pays out (the Folia retired-task leak fix,
 *       approximated against the real scheduler).</li>
 *   <li><strong>deathDropsBankedDamage</strong> — the victim's death clears its banked damage (the dead stay
 *       dead, ADR-0051); nothing lands afterwards.</li>
 *   <li><strong>comboEndReleaseCrossRegion</strong> — the release lands even when the bucket's attacker sits in
 *       a distinct Folia region: the guarded liveness read degrades to a bare hurt rather than throwing.</li>
 * </ul>
 *
 * <p>Modern-build suite (Mental is a modern-server plugin; the 1.8.9 lane's compile gate covers the leaf).
 */
public final class ComboDotSyncSuite implements Harness.Scenario {

    private static final double EPS = 1.0e-6;
    /** Fake players join with ~60-tick spawn invulnerability; a hit inside it no-ops with no event. Wait it out. */
    private static final long JOIN_PROTECTION_BUDGET = 70L;
    /** Parked amount used throughout — distinct from every base hit so a transposition cannot pass. */
    private static final double PARKED = 3.0;

    private final Plugin plugin;

    public ComboDotSyncSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);
        Compiler compiler = ContentCompiler.production(resolvers);
        Library library; // empty — these checks stage raw parked damage + real hits, no compiled ability needed
        try {
            library = LibraryLoader.load(Files.createTempDirectory("se-combodot-suite"), compiler, 0);
        } catch (IOException e) {
            h.fail("combodot.suiteBoot", e);
            return;
        }

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        checkParkWhileComboActive(h, handles, world, at);
        checkFlushJoinsARealHit(h, handles, library, world, at);
        checkComboEndReleaseWindowAware(h, world, at);
        checkActiveComboHoldsThirdPartyBuckets(h, handles, library, world, at);
        checkCancelledHitReparks(h, handles, library, world, at);
        checkClearMidReleaseAllowsAFreshBegin(h, world, at);
        checkDeathDropsBankedDamage(h, world, at);
        checkComboEndReleaseCrossRegion(h, world, at);
    }

    // ── 1: a deferred hit + a freeze DoT chain on a comboed victim bank instead of applying ──────────

    private void checkParkWhileComboActive(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "combodot.parkWhileComboActive";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv();
        DotParkLedger ledger = env.dotPark();
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_park_a");
                victim = rig.spawnFake(world, "se_cds_park_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger seLeaks = new AtomicInteger();                    // a leaked SE parked DoT (attributed to the attacker)
            AtomicReference<Double> vanillaFreeze = new AtomicReference<>(0.0); // the freeze VISUAL's own vanilla FREEZE damage
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageEvent e) {
                    if (!e.getEntity().getUniqueId().equals(victimId)) {
                        return;
                    }
                    // A leaked SE parked DoT is an attributed hurt (EngineDamage.hurt with the attacker in scope,
                    // so an EntityDamageByEntityEvent from the attacker). The freeze VISUAL's own vanilla FREEZE
                    // damage is a vanilla-mechanism DoT the design does NOT park (§7.9) — expected, not a leak.
                    if (e instanceof EntityDamageByEntityEvent edbe
                            && edbe.getDamager().getUniqueId().equals(attackerId)) {
                        seLeaks.incrementAndGet();
                    } else if (e.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                        vanillaFreeze.updateAndGet(v -> v + e.getFinalDamage());
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth()); // at max, no damage lands, no regen headroom ⇒ health is a clean invariant
                victim.setNoDamageTicks(0);
                ledger.comboStarted(victimId, attacker.getUniqueId(), 0L);
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.delay(5);
                sink.damage(victim, 4.0, attacker); // deferred DAMAGE (site #1) → hurtOrPark banks it
                sink.delay(0);
                sink.freeze(victim, 40, 2.0, 10, 5.0, true, attacker); // four DoT slots at t+10/20/30/40, each banked
                sink.flush();
                // Hold past the whole freeze window so all four slots have parked before we read the ledger.
                Scheduling.onEntityLater(victim, 60L, () -> {
                    double owed = drainSum(ledger, victimId, attacker.getUniqueId());
                    h.guard(key, () -> {
                        double freezeToll = vanillaFreeze.get();
                        if ((victim.getMaxHealth() - victim.getHealth()) - freezeToll > EPS) {
                            throw new IllegalStateException("a parked victim lost health beyond the design-excluded"
                                    + " vanilla freeze (" + freezeToll + "): " + victim.getHealth() + "/"
                                    + victim.getMaxHealth() + " — banked SE damage leaked through");
                        }
                        if (seLeaks.get() != 0) {
                            throw new IllegalStateException("a parked victim saw " + seLeaks.get()
                                    + " SE damage-over-time event(s); no engine DoT should reach vanilla mid-combo");
                        }
                        if (Math.abs(owed - 12.0) > EPS) {
                            throw new IllegalStateException("banked total " + owed
                                    + " != 12.0 (deferred 4.0 + four freeze slots × 2.0)");
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    // ── 2: the attacker's own parked bucket folds into their next real hit (one event, one window) ──

    private void checkFlushJoinsARealHit(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "combodot.flushJoinsARealHit";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = newCombat(rig, handles, library);
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_flush_a");
                victim = rig.spawnFake(world, "se_cds_flush_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            Map<UUID, Double> raw = new ConcurrentHashMap<>();
            Map<UUID, Double> committed = new ConcurrentHashMap<>();
            AtomicInteger events = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.LOWEST)
                public void pre(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)) {
                        raw.put(victimId, e.getDamage());
                    }
                }

                @EventHandler(priority = EventPriority.MONITOR)
                public void post(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)) {
                        committed.put(victimId, e.getDamage());
                        events.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                double before = victim.getHealth();
                ledger.comboStarted(victimId, attacker.getUniqueId(), 0L);
                ledger.tryPark(victimId, attacker, PARKED, 0L);
                victim.damage(2.0, attacker); // synchronous: dispatch drains bucket[A], folds owed, vanilla applies
                h.guard(key, () -> {
                    Double r = raw.get(victimId);
                    Double c = committed.get(victimId);
                    if (r == null || c == null) {
                        throw new IllegalStateException("no combat event captured; raw=" + r + " committed=" + c);
                    }
                    if (events.get() != 1) {
                        throw new IllegalStateException("expected exactly one damage event, saw " + events.get());
                    }
                    if (Math.abs(c - (r + PARKED)) > EPS) {
                        throw new IllegalStateException("committed " + c + " != raw " + r + " + parked " + PARKED
                                + " (the fold did not join the parked bucket)");
                    }
                    if (ledger.hasParked(victimId)) {
                        throw new IllegalStateException("the attacker's bucket was not drained by the flush");
                    }
                    if (Math.abs((before - victim.getHealth()) - c) > 0.5) {
                        throw new IllegalStateException("health delta " + (before - victim.getHealth())
                                + " != committed damage " + c);
                    }
                });
                rig.teardown();
            }));
        });
    }

    // ── 3: the combo-end release waits an armed window, then lands one attributed hit of the debt ──

    private void checkComboEndReleaseWindowAware(Harness h, World world, Location at) {
        final String key = "combodot.comboEndReleaseIsAttributedAndWindowAware";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = new DotParkLedger();
        ComboDotRelease release = new ComboDotRelease(ledger, () -> 0L);
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_rel_b");
                victim = rig.spawnFake(world, "se_cds_rel_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger releaseHits = new AtomicInteger();
            AtomicInteger byAttacker = new AtomicInteger();
            AtomicReference<Double> lastAmount = new AtomicReference<>();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (!e.getEntity().getUniqueId().equals(victimId)) {
                        return;
                    }
                    releaseHits.incrementAndGet();
                    lastAmount.set(e.getDamage());
                    if (e.getDamager().getUniqueId().equals(attackerId)) {
                        byAttacker.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                ledger.comboStarted(victimId, attackerId, 0L);
                ledger.tryPark(victimId, attacker, PARKED, 0L);
                victim.setNoDamageTicks(victim.getMaximumNoDamageTicks()); // arm the window: release must WAIT it out
                ledger.comboEnded(victimId);
                release.begin(victim);
                Scheduling.onEntityLater(victim, 3L, () -> {
                    int early = releaseHits.get(); // window still armed here → must be zero
                    // Now clear the window so the release lands promptly and deterministically. Fake players do
                    // NOT decay noDamageTicks on their own on 1.17.1, so relying on natural decay races the wait
                    // budget there (the release only forces through at WINDOW_WAIT_CAP_TICKS) — clear it, like
                    // clearMidReleaseAllowsAFreshBegin does.
                    victim.setNoDamageTicks(0);
                    Scheduling.onEntityLater(victim, 40L, () -> {
                        h.guard(key, () -> {
                            if (early != 0) {
                                throw new IllegalStateException("release fired " + early
                                        + " hit(s) while the victim's i-frame window was still armed");
                            }
                            if (releaseHits.get() != 1) {
                                throw new IllegalStateException("expected exactly one released hit, saw "
                                        + releaseHits.get());
                            }
                            if (byAttacker.get() != 1) {
                                throw new IllegalStateException("the released hit was not attributed to its attacker");
                            }
                            Double amt = lastAmount.get();
                            if (amt == null || Math.abs(amt - PARKED) > 0.5) {
                                throw new IllegalStateException("released amount " + amt + " != parked " + PARKED);
                            }
                            if (ledger.hasParked(victimId)) {
                                throw new IllegalStateException("ledger still holds a bucket after the release");
                            }
                        });
                        rig.teardown();
                    });
                });
            }));
        });
    }

    // ── 4: a third party's bucket stays parked across the holder's real hit (no mid-combo release) ──

    private void checkActiveComboHoldsThirdPartyBuckets(Harness h, RuntimeHandles handles, Library library,
                                                        World world, Location at) {
        final String key = "combodot.activeComboHoldsThirdPartyBuckets";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = newCombat(rig, handles, library);
        rig.onArena(at, () -> {
            Player holder;
            Player third;
            Player victim;
            try {
                holder = rig.spawnFake(world, "se_cds_hold_a");
                third = rig.spawnFake(world, "se_cds_hold_b");
                victim = rig.spawnFake(world, "se_cds_hold_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID thirdId = third.getUniqueId();
            AtomicInteger thirdAttributed = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(thirdId)) {
                        thirdAttributed.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                ledger.comboStarted(victimId, holder.getUniqueId(), 0L); // combo active on the victim
                ledger.tryPark(victimId, third, PARKED, 0L);             // a third party's banked bleed
                victim.damage(2.0, holder);                             // holder's real hit drains only bucket[holder]
                Scheduling.onEntityLater(victim, 20L, () -> {
                    double held = drainSum(ledger, victimId, thirdId);
                    h.guard(key, () -> {
                        if (Math.abs(held - PARKED) > EPS) {
                            throw new IllegalStateException("third-party bucket " + held + " != parked " + PARKED
                                    + " — an active combo must hold it, never release it");
                        }
                        if (thirdAttributed.get() != 0) {
                            throw new IllegalStateException("a third-party bucket released mid-combo ("
                                    + thirdAttributed.get() + " hit) — that would end the combo it protects");
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    // ── 5: a drained flush the event ends up cancelling is re-parked at MONITOR ─────────────────────

    private void checkCancelledHitReparks(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "combodot.cancelledHitReparks";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = newCombat(rig, handles, library); // registers CombatListener + ComboDotSyncListener
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_cancel_a");
                victim = rig.spawnFake(world, "se_cds_cancel_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            AtomicInteger applied = new AtomicInteger();
            // HIGHEST canceller (after CombatListener's HIGH drain-and-mark, before the MONITOR restore), filtered
            // to THIS victim so it never touches a concurrent check's hits.
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.HIGHEST)
                public void cancel(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)) {
                        e.setCancelled(true);
                    }
                }
            });
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void applied(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId) && !e.isCancelled()) {
                        applied.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                double before = victim.getHealth();
                ledger.comboStarted(victimId, attacker.getUniqueId(), 0L);
                ledger.tryPark(victimId, attacker, PARKED, 0L);
                victim.damage(2.0, attacker); // dispatch drains+marks; HIGHEST cancels; MONITOR re-parks
                h.guard(key, () -> {
                    if (applied.get() != 0) {
                        throw new IllegalStateException("a cancelled hit still applied " + applied.get() + " event(s)");
                    }
                    if (Math.abs(victim.getHealth() - before) > EPS) {
                        throw new IllegalStateException("victim lost health on a cancelled hit: "
                                + victim.getHealth() + " (was " + before + ")");
                    }
                    double restored = drainSum(ledger, victimId, attacker.getUniqueId());
                    if (Math.abs(restored - PARKED) > EPS) {
                        throw new IllegalStateException("bucket not re-parked after cancel; drained " + restored
                                + " != " + PARKED);
                    }
                });
                rig.teardown();
            }));
        });
    }

    // ── 6: a clear mid-release drops the in-flight guard, so a fresh begin arms + pays out ─────────

    private void checkClearMidReleaseAllowsAFreshBegin(Harness h, World world, Location at) {
        final String key = "combodot.clearMidReleaseAllowsAFreshBegin";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = new DotParkLedger();
        ComboDotRelease release = new ComboDotRelease(ledger, () -> 0L);
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_clear_b");
                victim = rig.spawnFake(world, "se_cds_clear_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID attackerId = attacker.getUniqueId();
            AtomicInteger payouts = new AtomicInteger();
            AtomicReference<Double> lastAmount = new AtomicReference<>();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(attackerId)) {
                        payouts.incrementAndGet();
                        lastAmount.set(e.getDamage());
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                ledger.comboStarted(victimId, attackerId, 0L);
                ledger.tryPark(victimId, attacker, PARKED, 0L);
                ledger.comboEnded(victimId);
                victim.setNoDamageTicks(victim.getMaximumNoDamageTicks()); // arm the window so the first loop is WAITING
                release.begin(victim);
                // The closest live approximation of a Folia quit-during-release: clear (drops buckets AND the
                // in-flight flag) before the first loop drained, then re-park and begin AGAIN — beginRelease must
                // succeed and pay out. (The true retired-task seam is unit-tested; a live task cannot be retired here.)
                Scheduling.onEntityLater(victim, 2L, () -> {
                    ledger.clear(victimId);
                    ledger.comboStarted(victimId, attackerId, 0L);
                    ledger.tryPark(victimId, attacker, PARKED, 0L);
                    ledger.comboEnded(victimId);
                    victim.setNoDamageTicks(0); // window clear so the fresh loop pays out promptly
                    release.begin(victim);
                    Scheduling.onEntityLater(victim, 30L, () -> {
                        h.guard(key, () -> {
                            if (payouts.get() != 1) {
                                throw new IllegalStateException("expected exactly one payout after clear+re-begin, saw "
                                        + payouts.get() + " (a leaked in-flight flag would have paid out zero)");
                            }
                            Double amt = lastAmount.get();
                            if (amt == null || Math.abs(amt - PARKED) > 0.5) {
                                throw new IllegalStateException("payout " + amt + " != re-parked " + PARKED);
                            }
                            if (ledger.hasParked(victimId)) {
                                throw new IllegalStateException("ledger not empty after the release");
                            }
                        });
                        rig.teardown();
                    });
                });
            }));
        });
    }

    // ── 7: the victim's death drops its banked damage (the dead stay dead, ADR-0051) ───────────────

    private void checkDeathDropsBankedDamage(Harness h, World world, Location at) {
        final String key = "combodot.deathDropsBankedDamage";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = new DotParkLedger();
        rig.listen(new ComboDotSyncListener(ledger)); // onDeath clears the victim's ledger
        rig.onArena(at, () -> {
            Player attacker;
            Player victim;
            try {
                attacker = rig.spawnFake(world, "se_cds_death_a");
                victim = rig.spawnFake(world, "se_cds_death_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            AtomicInteger postDeathEvents = new AtomicInteger();
            boolean[] killed = {false};
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId) && killed[0]) {
                        postDeathEvents.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                ledger.comboStarted(victimId, attacker.getUniqueId(), 0L);
                ledger.tryPark(victimId, attacker, PARKED, 0L);
                victim.setNoDamageTicks(0);
                victim.setHealth(2.0);
                victim.damage(1000.0, attacker); // lethal → PlayerDeathEvent → ComboDotSyncListener.onDeath clears
                killed[0] = true;
                Scheduling.onEntityLater(victim, 5L, () -> {
                    h.guard(key, () -> {
                        if (!victim.isDead()) {
                            throw new IllegalStateException("victim did not die under a lethal hit — staging broke");
                        }
                        if (ledger.hasParked(victimId)) {
                            throw new IllegalStateException("banked damage survived death (the dead must stay dead)");
                        }
                        if (postDeathEvents.get() != 0) {
                            throw new IllegalStateException("a parked hit landed after death: " + postDeathEvents.get());
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    // ── 8 (Folia): the release lands with the bucket's attacker in a DISTINCT region (guarded degrade) ──

    private void checkComboEndReleaseCrossRegion(Harness h, World world, Location at) {
        final String key = "combodot.comboEndReleaseCrossRegion";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        DotParkLedger ledger = new DotParkLedger();
        ComboDotRelease release = new ComboDotRelease(ledger, () -> 0L);
        Location farAt = at.clone().add(CrossRegion.GAP, 0, CrossRegion.GAP); // far enough to own a distinct Folia region
        rig.onArena(at, farAt, () -> {
            // The cross-region degrade only exists on a regionized server: on single-threaded Paper there are no
            // regions to cross (the far attacker is just a far entity), so this scenario is vacuous there — like
            // the other cross-region suites (CrossRegionTeleportSuite / AffinityAutogenSuite), skip it.
            if (!Capabilities.foliaPresent()) {
                plugin.getLogger().info("[combodot] single-threaded Paper — cross-region release degrade not applicable");
                h.pass(key);
                return;
            }
            Player victim;
            try {
                victim = rig.spawnFake(world, "se_cds_xr_v"); // FakePlayers spawns at world spawn (primary region)
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            AtomicInteger events = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageEvent e) { // EntityDamageEvent covers the bare-hurt degrade AND the attributed path
                    if (e.getEntity().getUniqueId().equals(victimId)) {
                        events.incrementAndGet();
                    }
                }
            });
            // Spawn the bucket's attacker in the FAR region, on that region's own thread.
            Scheduling.onRegion(farAt, () -> {
                LivingEntity farAttacker;
                try {
                    farAttacker = rig.spawn(world, farAt, EntityType.COW, LivingEntity.class);
                    farAttacker.setAI(false);
                } catch (Throwable t) {
                    h.fail(key, t);
                    rig.teardown();
                    return;
                }
                UUID farId = farAttacker.getUniqueId(); // captured on the far thread
                Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(victim, () -> {
                    victim.setHealth(victim.getMaxHealth());
                    victim.setNoDamageTicks(0);
                    ledger.comboStarted(victimId, farId, 0L);
                    ledger.tryPark(victimId, farAttacker, PARKED, 0L); // handle stored, never dereferenced here
                    ledger.comboEnded(victimId);
                    try {
                        // Runs on the victim's (primary) thread; the release's attacker-ownership check sees the far
                        // attacker is off-region and degrades to a BARE hurt, which lands — rather than attributing
                        // a cross-region damager (which vanilla dereferences → a wrong-thread throw).
                        release.begin(victim);
                    } catch (Throwable t) {
                        h.fail(key, "release.begin threw: " + t);
                        rig.teardown();
                        return;
                    }
                    // Poll for the released hurt's damage event rather than asserting at a fixed +40 ticks:
                    // the release schedules the bare hurt on the victim's scheduler, and under CI load that
                    // event lands later than 40 ticks — the flake. Fail only if it never lands within 120.
                    awaitUntil(victim, () -> events.get() >= 1, 0, 120, landed -> {
                        h.guard(key, () -> {
                            if (!landed) {
                                throw new IllegalStateException("the cross-region release never landed on the victim");
                            }
                            if (ledger.hasParked(victimId)) {
                                throw new IllegalStateException("the cross-region bucket was not drained");
                            }
                        });
                        rig.teardown();
                    });
                }));
            });
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /**
     * Poll {@code cond} once per tick on {@code entity}'s scheduler up to {@code maxTicks}, then hand the
     * callback whether it became true — the FreezeSuite/ReforgeMotionSuite idiom. Replaces fixed-tick asserts
     * that flake when a scheduled effect lands later than the hardcoded delay under CI load.
     */
    private static void awaitUntil(org.bukkit.entity.Entity entity, java.util.function.BooleanSupplier cond,
                                   int tick, int maxTicks, java.util.function.Consumer<Boolean> done) {
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

    /** A fresh isolated env (mirrors FreezeSuite) whose {@code dotPark()} the staged sink writes through. */
    private static SinkEnv freshEnv() {
        return SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(), () -> 0L);
    }

    /**
     * Wire a real {@link CombatDispatch} + {@link CombatListener} + {@link ComboDotSyncListener} on {@code rig}
     * (the ReHitOnceSuite/DeathRaceSuite shape) over a fresh env, and return the env's park ledger — the same
     * ledger the dispatch drains and the confirm re-parks. A constant tick keeps a marked combo active.
     */
    private static DotParkLedger newCombat(CombatRig rig, RuntimeHandles handles, Library library) {
        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        SinkEnv env = freshEnv();
        CombatDispatch dispatch = new CombatDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());
        rig.listen(new CombatListener(dispatch));
        rig.listen(new ComboDotSyncListener(env.dotPark()));
        return env.dotPark();
    }

    /** Drain a victim's buckets that a hit by {@code attacker} would join, summing the debt (also asserts none linger). */
    private static double drainSum(DotParkLedger ledger, UUID victim, UUID attacker) {
        double sum = 0.0;
        for (DotParkLedger.Bucket bucket : ledger.drainFor(victim, attacker)) {
            sum += bucket.amount();
        }
        return sum;
    }
}
