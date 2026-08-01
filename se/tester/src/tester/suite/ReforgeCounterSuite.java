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
import engine.sink.GuardianCasts;
import engine.sink.ModernDispatchSink;
import engine.sink.PetSummons;
import engine.sink.SinkEnv;
import engine.sink.SoulDebit;
import engine.sink.SummonFlags;
import engine.sink.SwarmClouds;
import engine.sink.TrapStructures;
import engine.stores.BatteryStore;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.stores.HitTempoStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.RageStacksListener;
import feature.combat.RageStacksService;
import feature.combat.ReforgeStrikeListener;
import feature.combat.ReforgeTempoGuardListener;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;
import platform.economy.EconomyService;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.CrossRegion;
import tester.harness.Harness;

/**
 * Plan-C reforge combat-state kinds, live (ADR-0071): HIT_TEMPO, BATTERY, DISARM_SHUFFLE, CONVERT_SUMMON and
 * TRAP_BREAK proven against the REAL vanilla damage pipeline, real inventories, real summoned mobs and the real
 * temp-block ledger — exactly the surfaces the floor-compiled unit tests cannot reach. Each check stands up its
 * OWN wiring (the tester installs only the tester jar — there is no production boot): a fresh {@link SinkEnv}, a
 * real {@link CombatDispatch}/{@link CombatListener} plus the two reforge listeners for the strike kinds, and the
 * static summon registries for the Bell. Captors filter by the acting UUID so the concurrently-launched checks
 * never contaminate one another (the ComboDotSyncSuite pattern), and every {@link CombatRig} unwinds its
 * listeners + actors on teardown.
 *
 * <p>Tester traps honored (reforge-contracts §3): fake players carry ~60-tick spawn invulnerability, so every
 * staged hit waits {@link #SPAWN_INVULN_TICKS} first, then sets {@code noDamageTicks} EXPLICITLY (fake players do
 * not decay it — the written value IS the contract, never wait for decay); {@link CombatDispatch.Caps#unlimited()}
 * = attack-scale 1.0, so felt-percent claims are ratios of two staged hits; hostile mobs are culled on peaceful,
 * so the summon checks use IRON_GOLEM / WOLF / COW-with-flags / BAT only; positions/targets are polled a few ticks
 * after spawn (never raced against vanilla AI drift).
 *
 * <p>Modern-build suite — HIT_TEMPO's attack-speed leaf and BLOCK_BELL_USE degrade on the 1.8.9 lane (its compile
 * gate covers the floor); {@code attackSpeedModifierReconciled} and the cross-region cage self-skip with a note off
 * a regionized server.
 */
public final class ReforgeCounterSuite implements Harness.Scenario {

    private static final double EPS = 1.0e-6;
    /** Fake players join with ~60-tick spawn invulnerability; a hit inside it no-ops with no event. Wait it out. */
    private static final long SPAWN_INVULN_TICKS = 70L;
    /** Sky arenas: open air over a forced chunk so cages/webs place cleanly and no wild mob intersects the census. */
    private static final int ARENA_Y = 200;

    private final Plugin plugin;

    public ReforgeCounterSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        RegistryResolvers resolvers = new RegistryResolvers();
        RuntimeHandles handles = new RuntimeHandles(resolvers);
        Compiler compiler = ContentCompiler.production(resolvers);
        Library library; // empty — these checks stage raw arms + real hits/summons, no compiled ability needed
        try {
            library = LibraryLoader.load(Files.createTempDirectory("se-reforge-counter-suite"), compiler, 0);
        } catch (IOException e) {
            h.fail("reforge.counter.suiteBoot", e);
            return;
        }

        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();

        // HIT_TEMPO — Quickening Fang
        checkTempoImmunityWrite(h, handles, library, world, spawn);
        checkTempoThirdPartyGate(h, world, spawn);
        checkTempoTaxesCommit(h, handles, library, world, spawn);
        checkTempoAttackSpeedModifier(h, handles, world, spawn);

        // BATTERY — Supernova Core
        checkBatteryBanks(h, handles, library, world, spawn);
        checkBatteryDischarge(h, handles, library, world, spawn);
        checkBatteryEmptyDischarge(h, handles, library, world, spawn);

        // DISARM_SHUFFLE — The Unhanding
        checkUnhandingShuffle(h, handles, library, world, spawn);
        checkUnhandingRageInterrupt(h, handles, library, world, spawn);
        checkUnhandingMalus(h, handles, library, world, spawn);

        // CONVERT_SUMMON — Summoner's Bell
        checkBellGuardRebinds(h, handles, resolvers, world, spawn.clone().add(200, 0, 0));
        checkBellTameableRetamed(h, handles, resolvers, world, spawn.clone().add(360, 0, 0));
        checkBellSentryFlagsPreserved(h, handles, resolvers, world, spawn.clone().add(520, 0, 0));
        checkBellOutOfRangeAndOwn(h, handles, resolvers, world, spawn.clone().add(680, 0, 0));
        checkBellBatCloudTurned(h, handles, resolvers, world, spawn.clone().add(860, 0, 0));

        // TRAP_BREAK — Turnkey
        checkTurnkeyCage(h, handles, resolvers, world, spawn.clone().add(1040, 0, 0));
        checkTurnkeyBoxAndWeb(h, handles, resolvers, world, spawn.clone().add(1200, 0, 0));
        checkTurnkeyFloorPaintUntouched(h, handles, resolvers, world, spawn.clone().add(1360, 0, 0));
        checkTurnkeyCrossRegionCage(h, handles, resolvers, world, spawn);
    }

    // ── HIT_TEMPO ────────────────────────────────────────────────────────────────────────────────

    /** 1: a landed holder hit rewrites the victim's i-frames to {@code max − (max/2)/2} and stamps the stolen span. */
    private void checkTempoImmunityWrite(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.quickening-fang.holderHitSlashesImmunityMental";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player victim;
            try {
                holder = rig.spawnFake(world, "se_rf_t1_h");
                victim = rig.spawnFake(world, "se_rf_t1_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID thirdId = UUID.randomUUID(); // a phantom third party the stolen stamp must gate
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.hitTempo(holder, 100, 0 /* MENTAL */, 1.0 / 3, 0.0);
                sink.flush();
                victim.setNoDamageTicks(0);
                victim.damage(4.0, holder); // MONITOR marks the tempo write (deferred one tick) + stamps stolen
                // The halving re-write lands ONE tick after the hit (production defers it past vanilla's own
                // post-event invulnerableTime reset) and then DECAYS 1/tick like any i-frame — a live fake
                // player ticks noDamageTicks on the server, so a single late equality read is wrong (it caught
                // the ladder two ticks decayed: 13 not 15). Sample the ladder instead and recover the write from
                // it below.
                List<Integer> ladder = new ArrayList<>();
                sampleNoDamage(victim, ladder, 4, () -> {
                    h.guard(key, () -> {
                        int max = victim.getMaximumNoDamageTicks();
                        int expected = max - (max / 2) / 2; // MENTAL: W = max/2, write = max − W/2 (15 at max 20)
                        // The halving write drops noDamageTicks BELOW the natural decay floor (max − k at k ticks
                        // in — vanilla i-frames decay at most 1/tick so can never fall below it), and the PEAK of
                        // that written-then-decaying ladder IS the write value. Recover it as the largest
                        // below-floor sample: it proves a write landed (cannot false-pass vanilla decay) and pins
                        // the value to `expected`, tolerating the one tick on which the entity's own decrement
                        // races our read.
                        int peak = Integer.MIN_VALUE;
                        for (int k = 1; k <= ladder.size(); k++) {
                            int sample = ladder.get(k - 1);
                            if (sample < max - k) {
                                peak = Math.max(peak, sample);
                            }
                        }
                        if (peak == Integer.MIN_VALUE) {
                            throw new IllegalStateException("tempo i-frame ladder " + ladder + " never dropped below "
                                    + "the natural decay floor — the halving write did not land (max " + max + ")");
                        }
                        if (peak < expected - 1 || peak > expected) {
                            throw new IllegalStateException("tempo i-frame write peak = " + peak + " != expected "
                                    + expected + " (±1 tick) — ladder " + ladder + " (max " + max + ")");
                        }
                        HitTempoStore store = env.stores().hitTempo();
                        if (!store.stolenBlocks(victimId, thirdId, 0L)) {
                            throw new IllegalStateException("the stolen stamp did not gate a third-party attacker");
                        }
                        if (store.stolenBlocks(victimId, holder.getUniqueId(), 0L)) {
                            throw new IllegalStateException("the holder was gated by their own stolen interval");
                        }
                    });
                    rig.teardown();
                });
            }));
        });
    }

    /** 2: inside the stolen span a non-holder's hit is cancelled once; after the span the same hit lands. */
    private void checkTempoThirdPartyGate(Harness h, World world, Location at) {
        final String key = "reforge.quickening-fang.thirdPartyHitGatedExactlyOnce";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        long[] clock = {0L};
        HitTempoStore store = new HitTempoStore();
        rig.listen(new ReforgeTempoGuardListener(store, () -> clock[0]));
        rig.onArena(at, () -> {
            Player victim;
            Player third;
            try {
                victim = rig.spawnFake(world, "se_rf_t2_v");
                third = rig.spawnFake(world, "se_rf_t2_t");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID thirdId = third.getUniqueId();
            UUID holderId = UUID.randomUUID(); // the phantom holder that owns the live stolen interval
            store.stampStolen(victimId, holderId, 10L); // live for [0, 10)
            List<Boolean> cancelled = new ArrayList<>();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(victimId)
                            && e.getDamager().getUniqueId().equals(thirdId)) {
                        cancelled.add(e.isCancelled());
                    }
                }
            });
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(victim, () -> {
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                double before = victim.getHealth();
                clock[0] = 0L;
                victim.damage(4.0, third); // guard cancels at LOWEST (interval live, third holds none)
                double afterGate = victim.getHealth();
                clock[0] = 20L;            // past max/2 = 10 → the stamp lapses
                victim.setNoDamageTicks(0);
                victim.damage(4.0, third); // guard passes now → the hit lands
                double afterLand = victim.getHealth();
                h.guard(key, () -> {
                    if (cancelled.size() != 2) {
                        throw new IllegalStateException("expected exactly two observed third-party events, saw "
                                + cancelled.size());
                    }
                    if (!cancelled.get(0)) {
                        throw new IllegalStateException("the in-span third-party hit was not cancelled");
                    }
                    if (Math.abs(afterGate - before) > EPS) {
                        throw new IllegalStateException("the cancelled hit still cost the victim health");
                    }
                    if (cancelled.get(1)) {
                        throw new IllegalStateException("the after-span third-party hit was still cancelled");
                    }
                    if (afterLand >= afterGate - 0.5) {
                        throw new IllegalStateException("the after-span hit did not land: " + afterLand
                                + " vs " + afterGate);
                    }
                });
                rig.teardown();
            }));
        });
    }

    /** 3: a windowed melee commit is taxed to the factor of an un-windowed one (ratio, no magic constant). */
    private void checkTempoTaxesCommit(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.quickening-fang.windowTaxesCommit";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player baseVictim;
            Player taxedVictim;
            try {
                holder = rig.spawnFake(world, "se_rf_t3_h");
                baseVictim = rig.spawnFake(world, "se_rf_t3_b");
                taxedVictim = rig.spawnFake(world, "se_rf_t3_x");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            AtomicReference<Double> baseline = new AtomicReference<>();
            AtomicReference<Double> taxed = new AtomicReference<>();
            captureCommitted(rig, baseVictim.getUniqueId(), baseline);
            captureCommitted(rig, taxedVictim.getUniqueId(), taxed);
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(holder, () -> {
                baseVictim.setHealth(baseVictim.getMaxHealth());
                baseVictim.setNoDamageTicks(0);
                baseVictim.damage(6.0, holder); // no window → committed == base
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.hitTempo(holder, 100, 0, 1.0 / 3, 0.0);
                sink.flush();
                taxedVictim.setHealth(taxedVictim.getMaxHealth());
                taxedVictim.setNoDamageTicks(0);
                taxedVictim.damage(6.0, holder); // window → committed × 1/3
                h.guard(key, () -> {
                    Double b = baseline.get();
                    Double x = taxed.get();
                    if (b == null || x == null) {
                        throw new IllegalStateException("missing committed reads: base=" + b + " taxed=" + x);
                    }
                    double ratio = x / b;
                    if (Math.abs(ratio - 1.0 / 3) > 1.0e-3) {
                        throw new IllegalStateException("tempo tax ratio " + ratio + " != 1/3 (base " + b
                                + ", taxed " + x + ")");
                    }
                });
                rig.teardown();
            }));
        });
    }

    /** 4 (modern): the 1.9+ arm reconciles exactly one owned attack-speed modifier, gone after the window. */
    private void checkTempoAttackSpeedModifier(Harness h, RuntimeHandles handles, World world, Location at) {
        final String key = "reforge.quickening-fang.attackSpeedModifierReconciled";
        h.expect(key);
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_ATTACK_SPEED");
        if (!(attribute instanceof Attribute attackSpeed)) {
            plugin.getLogger().info("[reforge] no attack-speed attribute (pre-1.9 lane) — modifier check skipped");
            h.pass(key);
            return;
        }
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        rig.onArena(at, () -> {
            Player holder;
            try {
                holder = rig.spawnFake(world, "se_rf_t4_h");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            Scheduling.onEntity(holder, () -> {
                AttributeInstance instance = holder.getAttribute(attackSpeed);
                if (instance == null) {
                    plugin.getLogger().info("[reforge] attack-speed unresolved on this player — modifier check skipped");
                    h.pass(key);
                    rig.teardown();
                    return;
                }
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.hitTempo(holder, 40, 1 /* VANILLA */, 1.0 / 3, 1.0);
                sink.flush();
                // The attack-speed modifier applies on the holder's region thread — inline on Paper, next tick
                // on Folia (the entity scheduler never runs inline) — so poll rather than read synchronously.
                awaitUntil(holder, () -> countTempoModifiers(instance) == 1, 0, 5, armed -> {
                    h.guard(key + ".armed", () -> {
                        if (!armed) {
                            throw new IllegalStateException("expected exactly one reforge_tempo modifier, saw "
                                    + countTempoModifiers(instance));
                        }
                    });
                    Scheduling.onEntityLater(holder, 45L, () -> {
                        h.guard(key, () -> {
                            AttributeInstance live = holder.getAttribute(attackSpeed);
                            if (live == null || countTempoModifiers(live) != 0) {
                                throw new IllegalStateException("the reforge_tempo modifier survived its TimedRevert");
                            }
                        });
                        rig.teardown();
                    });
                });
            });
        });
    }

    private static int countTempoModifiers(AttributeInstance instance) {
        int n = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getName() != null && modifier.getName().toLowerCase().contains("reforge_tempo")) {
                n++;
            }
        }
        return n;
    }

    /** Collect {@code victim.getNoDamageTicks()} once per tick for {@code samples} ticks (starting next tick),
     *  each read on the victim's own region thread, then run {@code done} — the tempo i-frame decay ladder. */
    private static void sampleNoDamage(LivingEntity victim, List<Integer> out, int samples, Runnable done) {
        Scheduling.onEntityLater(victim, 1L, () -> {
            out.add(victim.getNoDamageTicks());
            if (samples <= 1) {
                done.run();
            } else {
                sampleNoDamage(victim, out, samples - 1, done);
            }
        });
    }

    // ── BATTERY ─────────────────────────────────────────────────────────────────────────────────

    /** 5: the next {@code hits} taken each bank the fraction of their final damage; past the cap nothing banks. */
    private void checkBatteryBanks(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.supernova-core.banksFinalDamage";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player enemy;
            try {
                holder = rig.spawnFake(world, "se_rf_b5_h");
                enemy = rig.spawnFake(world, "se_rf_b5_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID holderId = holder.getUniqueId();
            AtomicReference<Double> lastFinal = new AtomicReference<>(0.0);
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(holderId)) {
                        lastFinal.set(e.getFinalDamage());
                    }
                }
            });
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(holder, () -> {
                BatteryStore battery = env.stores().battery();
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armBattery(holder, 0.2, 3);
                sink.flush();
                double f1 = takeHit(holder, enemy, 3.0, lastFinal);
                double f2 = takeHit(holder, enemy, 4.0, lastFinal);
                double afterTwo = battery.peek(holderId);
                double f3 = takeHit(holder, enemy, 5.0, lastFinal);
                takeHit(holder, enemy, 6.0, lastFinal); // 4th hit is past the 3-hit cap → banks nothing
                double afterFour = battery.peek(holderId);
                h.guard(key, () -> {
                    if (Math.abs(afterTwo - 0.2 * (f1 + f2)) > 1.0e-3) {
                        throw new IllegalStateException("banked after two = " + afterTwo + " != 0.2×(" + f1 + "+"
                                + f2 + ")");
                    }
                    if (Math.abs(afterFour - 0.2 * (f1 + f2 + f3)) > 1.0e-3) {
                        throw new IllegalStateException("banked after the cap = " + afterFour + " != 0.2×(" + f1 + "+"
                                + f2 + "+" + f3 + ") — the 4th hit was not refused");
                    }
                });
                rig.teardown();
            }));
        });
    }

    /** One landed hit TAKEN by {@code target} from {@code attacker}; returns the observed final damage. */
    private static double takeHit(Player target, Player attacker, double amount, AtomicReference<Double> lastFinal) {
        target.setHealth(target.getMaxHealth());
        target.setNoDamageTicks(0);
        target.damage(amount, attacker);
        return lastFinal.get();
    }

    /** 6: the discharge rides the SAME hit (one event, base + banked), then the core is spent. */
    private void checkBatteryDischarge(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.supernova-core.dischargeRidesSameHit";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player enemy;
            try {
                holder = rig.spawnFake(world, "se_rf_b6_h");
                enemy = rig.spawnFake(world, "se_rf_b6_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID holderId = holder.getUniqueId();
            UUID enemyId = enemy.getUniqueId();
            AtomicReference<Double> lastFinal = new AtomicReference<>(0.0);
            AtomicInteger enemyHits = new AtomicInteger();
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (e.getEntity().getUniqueId().equals(holderId)) {
                        lastFinal.set(e.getFinalDamage());
                    } else if (e.getEntity().getUniqueId().equals(enemyId)
                            && e.getDamager().getUniqueId().equals(holderId)) {
                        enemyHits.incrementAndGet();
                    }
                }
            });
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(holder, () -> {
                BatteryStore battery = env.stores().battery();
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armBattery(holder, 0.2, 3);
                sink.flush();
                takeHit(holder, enemy, 5.0, lastFinal); // banks 0.2 × 5.0 = 1.0
                double banked = battery.peek(holderId);
                double base = 4.0;
                enemy.setHealth(enemy.getMaxHealth());
                enemy.setNoDamageTicks(0);
                double before = enemy.getHealth();
                enemy.damage(base, holder); // discharge rides this hit
                double delta = before - enemy.getHealth();
                h.guard(key, () -> {
                    if (banked <= 0.0) {
                        throw new IllegalStateException("nothing banked before the discharge (banked " + banked + ")");
                    }
                    if (Math.abs(delta - (base + banked)) > 0.5) {
                        throw new IllegalStateException("enemy delta " + delta + " != base " + base + " + banked "
                                + banked);
                    }
                    if (enemyHits.get() != 1) {
                        throw new IllegalStateException("the discharge was not a single same-hit rider: "
                                + enemyHits.get() + " events");
                    }
                    if (battery.armed(holderId)) {
                        throw new IllegalStateException("the core survived its discharge");
                    }
                });
                rig.teardown();
            }));
        });
    }

    /** 7: a hit with nothing banked still spends the core; the enemy takes only the base. */
    private void checkBatteryEmptyDischarge(Harness h, RuntimeHandles handles, Library library, World world,
                                            Location at) {
        final String key = "reforge.supernova-core.emptyDischargeSpendsCore";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player enemy;
            try {
                holder = rig.spawnFake(world, "se_rf_b7_h");
                enemy = rig.spawnFake(world, "se_rf_b7_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID holderId = holder.getUniqueId();
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(holder, () -> {
                BatteryStore battery = env.stores().battery();
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armBattery(holder, 0.2, 3);
                sink.flush();
                double base = 4.0;
                enemy.setHealth(enemy.getMaxHealth());
                enemy.setNoDamageTicks(0);
                double before = enemy.getHealth();
                enemy.damage(base, holder); // 0 banked → the rider is a no-op, but the core is spent
                double delta = before - enemy.getHealth();
                h.guard(key, () -> {
                    if (Math.abs(delta - base) > 0.5) {
                        throw new IllegalStateException("empty discharge changed the base hit: delta " + delta
                                + " != " + base);
                    }
                    if (battery.armed(holderId)) {
                        throw new IllegalStateException("the empty core was not spent");
                    }
                });
                rig.teardown();
            }));
        });
    }

    // ── DISARM_SHUFFLE ────────────────────────────────────────────────────────────────────────────

    /** 8: a windowed hit swaps the victim's held stack into exactly one other hotbar slot; selection unchanged. */
    private void checkUnhandingShuffle(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.the-unhanding.shuffleSwapsHeldItem";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player victim;
            try {
                holder = rig.spawnFake(world, "se_rf_u8_h");
                victim = rig.spawnFake(world, "se_rf_u8_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID holderId = holder.getUniqueId();
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(victim, () -> {
                // Distinct stacks in all nine hotbar slots so exactly two changed slots pin the swap.
                for (int i = 0; i < 9; i++) {
                    victim.getInventory().setItem(i, new ItemStack(Material.DIAMOND, i + 1));
                }
                victim.getInventory().setHeldItemSlot(0);
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armDisarmShuffle(holder, 80, 0.2);
                sink.flush();
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                victim.damage(4.0, holder); // MONITOR consumes the window + shuffles the victim's hotbar
                h.guard(key, () -> {
                    List<Integer> changed = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        ItemStack now = victim.getInventory().getItem(i);
                        int amount = now == null ? 0 : now.getAmount();
                        if (amount != i + 1) {
                            changed.add(i);
                        }
                    }
                    if (changed.size() != 2 || !changed.contains(0)) {
                        throw new IllegalStateException("shuffle changed slots " + changed
                                + " (expected exactly {0, t})");
                    }
                    int target = changed.get(0) == 0 ? changed.get(1) : changed.get(0);
                    int inHeld = amountAt(victim, 0);
                    int inTarget = amountAt(victim, target);
                    if (inHeld != target + 1 || inTarget != 1) {
                        throw new IllegalStateException("held/target did not swap: slot0=" + inHeld + " slot" + target
                                + "=" + inTarget);
                    }
                    if (victim.getInventory().getHeldItemSlot() != 0) {
                        throw new IllegalStateException("the held-slot selection moved (should be shuffled, not locked)");
                    }
                    if (env.stores().disarmWindows().armed(holderId, 0L) != null) {
                        throw new IllegalStateException("the one-shot unhanding window was not consumed");
                    }
                });
                rig.teardown();
            }));
        });
    }

    private static int amountAt(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        return item == null ? 0 : item.getAmount();
    }

    /** 9: shuffling the victim's rage weapon away structurally interrupts their run — the next swing cannot rebuild. */
    private void checkUnhandingRageInterrupt(Harness h, RuntimeHandles handles, Library library, World world,
                                             Location at) {
        final String key = "reforge.the-unhanding.rageRunInterrupted";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        // rageLevelOf reads the held slot: only a DIAMOND_SWORD in hand carries rage (level 2).
        Function<Player, Integer> rageLevelOf = p -> {
            ItemStack main = p.getInventory().getItemInMainHand();
            return main != null && main.getType() == Material.DIAMOND_SWORD ? 2 : 0;
        };
        RageStacksService rage = new RageStacksService(rageLevelOf, env.stores().combo(),
                env.stores().rageStacks(), Messages.defaults(), Stores.sounds(), env.nowTicks());
        rig.listen(new RageStacksListener(rage));
        rig.onArena(at, () -> {
            Player holder;
            Player victim;
            LivingEntity dummy;
            try {
                holder = rig.spawnFake(world, "se_rf_u9_h");
                victim = rig.spawnFake(world, "se_rf_u9_v");
                dummy = rig.spawn(world, at.clone().add(1, 0, 0), EntityType.COW, LivingEntity.class);
                dummy.setAI(false);
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(victim, () -> {
                victim.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD)); // rage weapon in slot 0 only
                victim.getInventory().setHeldItemSlot(0);
                // Seed a live run: the victim lands two rage-weapon hits on the dummy.
                swingAt(victim, dummy, 2.0);
                swingAt(victim, dummy, 2.0);
                int seeded = env.stores().rageStacks().current(victimId);
                // The Unhanding: the holder lands a shuffle hit on the victim (also breaks the run via onHitTaken).
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armDisarmShuffle(holder, 80, 0.2);
                sink.flush();
                victim.setHealth(victim.getMaxHealth());
                victim.setNoDamageTicks(0);
                victim.damage(4.0, holder); // shuffle sends the sword out of the held slot
                // The victim's next swing: the held slot is now empty → rageLevelOf 0 → the run cannot rebuild.
                swingAt(victim, dummy, 2.0);
                int afterSwing = env.stores().rageStacks().current(victimId);
                h.guard(key, () -> {
                    if (seeded <= 0) {
                        throw new IllegalStateException("the rage run never seeded (current " + seeded + ")");
                    }
                    ItemStack held = victim.getInventory().getItemInMainHand();
                    if (held != null && held.getType() == Material.DIAMOND_SWORD) {
                        throw new IllegalStateException("the shuffle left the rage weapon in hand");
                    }
                    if (afterSwing != 0) {
                        throw new IllegalStateException("the disarmed victim's swing rebuilt the run to " + afterSwing);
                    }
                });
                rig.teardown();
            }));
        });
    }

    /** One landed swing by {@code attacker} on {@code target}, resetting i-frames first. */
    private static void swingAt(Player attacker, LivingEntity target, double amount) {
        target.setNoDamageTicks(0);
        target.damage(amount, attacker);
    }

    /** 10: the shuffling hit is felt −20% — the ratio of a windowed to an un-windowed commit is 0.8. */
    private void checkUnhandingMalus(Harness h, RuntimeHandles handles, Library library, World world, Location at) {
        final String key = "reforge.the-unhanding.malusFeltTwentyPercent";
        h.expect(key);
        CombatRig rig = new CombatRig(plugin);
        SinkEnv env = freshEnv(() -> 0L);
        wireCombat(rig, handles, library, env);
        rig.onArena(at, () -> {
            Player holder;
            Player baseVictim;
            Player taxedVictim;
            try {
                holder = rig.spawnFake(world, "se_rf_x10_h");
                baseVictim = rig.spawnFake(world, "se_rf_x10_b");
                taxedVictim = rig.spawnFake(world, "se_rf_x10_x");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            AtomicReference<Double> baseline = new AtomicReference<>();
            AtomicReference<Double> taxed = new AtomicReference<>();
            captureCommitted(rig, baseVictim.getUniqueId(), baseline);
            captureCommitted(rig, taxedVictim.getUniqueId(), taxed);
            Scheduling.onRegionLater(at, SPAWN_INVULN_TICKS, () -> Scheduling.onEntity(holder, () -> {
                baseVictim.setHealth(baseVictim.getMaxHealth());
                baseVictim.setNoDamageTicks(0);
                baseVictim.damage(6.0, holder); // no window → committed == base
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.armDisarmShuffle(holder, 80, 0.2);
                sink.flush();
                taxedVictim.setHealth(taxedVictim.getMaxHealth());
                taxedVictim.setNoDamageTicks(0);
                taxedVictim.damage(6.0, holder); // window → committed × 0.8
                h.guard(key, () -> {
                    Double b = baseline.get();
                    Double x = taxed.get();
                    if (b == null || x == null) {
                        throw new IllegalStateException("missing committed reads: base=" + b + " taxed=" + x);
                    }
                    double ratio = x / b;
                    if (Math.abs(ratio - 0.8) > 1.0e-3) {
                        throw new IllegalStateException("unhanding malus ratio " + ratio + " != 0.8 (base " + b
                                + ", taxed " + x + ")");
                    }
                });
                rig.teardown();
            }));
        });
    }

    // ── CONVERT_SUMMON ────────────────────────────────────────────────────────────────────────────

    /** 11: a guard owned by the enemy rebinds to the ringer and turns on its former owner. */
    private void checkBellGuardRebinds(Harness h, RuntimeHandles handles, RegistryResolvers resolvers, World world,
                                       Location arena) {
        final String key = "reforge.summoners-bell.guardOwnershipRebinds";
        h.expect(key);
        int golemId = resolveId(resolvers.entityType("IRON_GOLEM"), "IRON_GOLEM");
        Location sky = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), sky, () -> {
            Player ringer;
            Player enemy;
            try {
                ringer = rig.spawnFake(world, "se_rf_c11_r");
                enemy = rig.spawnFake(world, "se_rf_c11_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            hop(ringer, sky, () -> hop(enemy, sky.clone().add(2, 0, 0), () -> Scheduling.onRegion(sky, () -> {
                ModernDispatchSink spawnSink = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                spawnSink.guard(ringer, sky.clone().add(1, 0, 0), golemId, 1, 600, "&bBellGuard",
                        enemy.getUniqueId(), 0.0, 0.0, 0, 0.0, 20, 0, -1, 1.0f, 1.0f); // target=ringer initially, owned by the enemy
                spawnSink.flush();
                Scheduling.onRegionLater(sky, 3L, () -> {
                    Mob golem = findMob(world, sky, EntityType.IRON_GOLEM);
                    if (golem == null) {
                        h.fail(key, "no owned guard spawned");
                        rig.teardown();
                        return;
                    }
                    rig.track(golem);
                    UUID golemId2 = golem.getUniqueId();
                    if (!enemy.getUniqueId().equals(GuardianCasts.owner(golemId2))) {
                        h.fail(key, "guard was not bound to the enemy owner before conversion");
                        forgetSummons(golemId2);
                        rig.teardown();
                        return;
                    }
                    // The turn this check asserts is vanilla-IMPOSSIBLE at the matrix's PEACEFUL difficulty on
                    // modern: NeutralMob.updatePersistentAnger (IronGolem calls it with bl=true every AI step,
                    // javap-verified 26.1.2) runs stopBeingAngry() — nulling the target — every tick its PLAYER
                    // anger target is creative, spectator, or the world is peaceful. Under any OTHER difficulty
                    // the very same method auto-backs a bare setTarget with persistent anger the next tick, so
                    // production needs no write of its own (the floor, predating the peaceful-player clear,
                    // keeps the target either way). Hold NORMAL for just the conversion window — difficulty is
                    // level data (global thread), set BEFORE the bell so the forced target is never cleared
                    // once — and restore the prior difficulty on every exit.
                    Difficulty[] prior = new Difficulty[1];
                    Runnable restore = () -> Scheduling.onGlobal(() -> {
                        if (prior[0] != null) {
                            world.setDifficulty(prior[0]);
                        }
                    });
                    Scheduling.onGlobal(() -> {
                        prior[0] = world.getDifficulty();
                        world.setDifficulty(Difficulty.NORMAL);
                        Scheduling.onRegionLater(sky, 2L, () -> {
                            ModernDispatchSink bell = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                            bell.convertSummons(ringer, 12.0, -1);
                            bell.flush();
                            if (!ringer.getUniqueId().equals(GuardianCasts.owner(golemId2))) {
                                h.fail(key, "ownership did not rebind to the ringer");
                                forgetSummons(golemId2);
                                restore.run();
                                rig.teardown();
                                return;
                            }
                            awaitUntil(golem, () -> golem.getTarget() != null
                                    && golem.getTarget().getUniqueId().equals(enemy.getUniqueId()), 0, 10, hit -> {
                                h.guard(key, () -> {
                                    if (!hit) {
                                        throw new IllegalStateException(
                                                "the converted guard did not target its former owner");
                                    }
                                });
                                forgetSummons(golemId2);
                                restore.run();
                                rig.teardown();
                            });
                        });
                    });
                });
            })));
        });
    }

    /** 12: a Tameable owned by the enemy re-tames to the ringer. */
    private void checkBellTameableRetamed(Harness h, RuntimeHandles handles, RegistryResolvers resolvers, World world,
                                          Location arena) {
        final String key = "reforge.summoners-bell.tameableRetamed";
        h.expect(key);
        int wolfId = resolveId(resolvers.entityType("WOLF"), "WOLF");
        Location sky = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), sky, () -> {
            Player ringer;
            Player enemy;
            try {
                ringer = rig.spawnFake(world, "se_rf_c12_r");
                enemy = rig.spawnFake(world, "se_rf_c12_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            hop(ringer, sky, () -> hop(enemy, sky.clone().add(2, 0, 0), () -> Scheduling.onRegion(sky, () -> {
                ModernDispatchSink spawnSink = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                spawnSink.spawnEntity(sky.clone().add(1, 0, 0), wolfId, 1, 600, 8.0, enemy.getUniqueId());
                spawnSink.flush();
                Scheduling.onRegionLater(sky, 3L, () -> {
                    Mob wolf = findMob(world, sky, EntityType.WOLF);
                    if (wolf == null) {
                        h.fail(key, "no owned wolf spawned");
                        rig.teardown();
                        return;
                    }
                    rig.track(wolf);
                    UUID wolfUid = wolf.getUniqueId();
                    ModernDispatchSink bell = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                    bell.convertSummons(ringer, 12.0, -1);
                    bell.flush();
                    awaitUntil(wolf, () -> wolf instanceof Tameable t
                            && t.getOwner() != null
                            && t.getOwner().getUniqueId().equals(ringer.getUniqueId()), 0, 10, retamed -> {
                        h.guard(key, () -> {
                            if (!retamed) {
                                throw new IllegalStateException("the converted wolf did not re-tame to the ringer");
                            }
                            if (!ringer.getUniqueId().equals(GuardianCasts.owner(wolfUid))) {
                                throw new IllegalStateException("wolf ownership did not rebind in the registry");
                            }
                        });
                        forgetSummons(wolfUid);
                        rig.teardown();
                    });
                });
            })));
        });
    }

    /** 13: a noTarget sentry rebinds ownership but keeps its flags and takes no forced target. */
    private void checkBellSentryFlagsPreserved(Harness h, RuntimeHandles handles, RegistryResolvers resolvers,
                                               World world, Location arena) {
        final String key = "reforge.summoners-bell.sentryFlagsPreserved";
        h.expect(key);
        int cowId = resolveId(resolvers.entityType("COW"), "COW");
        SummonFlags flags = new SummonFlags(false, false, true /* noTarget */, false, false, false,
                true /* invincible */, 0.0, "", 0);
        Location sky = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), sky, () -> {
            Player ringer;
            Player enemy;
            try {
                ringer = rig.spawnFake(world, "se_rf_c13_r");
                enemy = rig.spawnFake(world, "se_rf_c13_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            hop(ringer, sky, () -> hop(enemy, sky.clone().add(2, 0, 0), () -> Scheduling.onRegion(sky, () -> {
                ModernDispatchSink spawnSink = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                spawnSink.spawnSummon(sky.clone().add(1, 0, 0), cowId, 1, 600, 20.0, enemy.getUniqueId(), null, flags);
                spawnSink.flush();
                Scheduling.onRegionLater(sky, 3L, () -> {
                    Mob sentry = findMob(world, sky, EntityType.COW);
                    if (sentry == null) {
                        h.fail(key, "no owned sentry spawned");
                        rig.teardown();
                        return;
                    }
                    rig.track(sentry);
                    UUID sentryUid = sentry.getUniqueId();
                    ModernDispatchSink bell = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                    bell.convertSummons(ringer, 12.0, -1);
                    bell.flush();
                    Scheduling.onRegionLater(sky, 6L, () -> {
                        h.guard(key, () -> {
                            if (!ringer.getUniqueId().equals(GuardianCasts.owner(sentryUid))) {
                                throw new IllegalStateException("sentry ownership did not rebind to the ringer");
                            }
                            if (!flags.equals(PetSummons.flags(sentryUid))) {
                                throw new IllegalStateException("the sentry's flags were altered by conversion: "
                                        + PetSummons.flags(sentryUid));
                            }
                            if (sentry.getTarget() != null) {
                                throw new IllegalStateException("a noTarget sentry was given a forced target");
                            }
                        });
                        forgetSummons(sentryUid);
                        rig.teardown();
                    });
                });
            })));
        });
    }

    /** 14: the ringer's own summon and an out-of-range enemy summon are both untouched. */
    private void checkBellOutOfRangeAndOwn(Harness h, RuntimeHandles handles, RegistryResolvers resolvers,
                                           World world, Location arena) {
        final String key = "reforge.summoners-bell.outOfRangeAndOwnUntouched";
        h.expect(key);
        int golemId = resolveId(resolvers.entityType("IRON_GOLEM"), "IRON_GOLEM");
        Location sky = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        Location farGolemAt = sky.clone().add(20, 0, 0); // outside the 12-block bell radius
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), sky, () -> {
            Player ringer;
            Player enemy;
            try {
                ringer = rig.spawnFake(world, "se_rf_c14_r");
                enemy = rig.spawnFake(world, "se_rf_c14_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            hop(ringer, sky, () -> hop(enemy, sky.clone().add(2, 0, 0), () -> Scheduling.onRegion(sky, () -> {
                ModernDispatchSink spawnSink = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                spawnSink.guard(enemy, sky.clone().add(1, 0, 0), golemId, 1, 600, "&aOwnGuard",
                        ringer.getUniqueId(), 0.0, 0.0, 0, 0.0, 20, 0, -1, 1.0f, 1.0f); // already the ringer's, near the ringer
                spawnSink.flush();
                Scheduling.onRegion(farGolemAt, () -> {
                    ModernDispatchSink farSink = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                    farSink.guard(ringer, farGolemAt, golemId, 1, 600, "&cFarGuard", enemy.getUniqueId(),
                            0.0, 0.0, 0, 0.0, 20, 0, -1, 1.0f, 1.0f);
                    farSink.flush();
                    Scheduling.onRegionLater(sky, 4L, () -> {
                        Mob own = findNamedMob(world, sky, EntityType.IRON_GOLEM, "OwnGuard");
                        Mob far = findNamedMob(world, farGolemAt, EntityType.IRON_GOLEM, "FarGuard");
                        if (own == null || far == null) {
                            h.fail(key, "guards not both present: own=" + own + " far=" + far);
                            rig.teardown();
                            return;
                        }
                        rig.track(own);
                        rig.track(far);
                        UUID ownUid = own.getUniqueId();
                        UUID farUid = far.getUniqueId();
                        ModernDispatchSink bell = new ModernDispatchSink(handles, freshEnv(() -> 0L));
                        bell.convertSummons(ringer, 12.0, -1);
                        bell.flush();
                        Scheduling.onRegionLater(sky, 6L, () -> {
                            h.guard(key, () -> {
                                if (!ringer.getUniqueId().equals(GuardianCasts.owner(ownUid))) {
                                    throw new IllegalStateException("the ringer's own guard was rebound");
                                }
                                if (!enemy.getUniqueId().equals(GuardianCasts.owner(farUid))) {
                                    throw new IllegalStateException("an out-of-range enemy guard was converted");
                                }
                            });
                            forgetSummons(ownUid);
                            forgetSummons(farUid);
                            rig.teardown();
                        });
                    });
                });
            })));
        });
    }

    /** 15: an enemy bat cloud is TURNED — its pillar permanently publishes at the FORMER owner's own position. */
    private void checkBellBatCloudTurned(Harness h, RuntimeHandles handles, RegistryResolvers resolvers, World world,
                                         Location arena) {
        final String key = "reforge.summoners-bell.batCloudTurned";
        h.expect(key);
        int batId = resolveId(resolvers.entityType("BAT"), "BAT");
        Location sky = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        CombatRig rig = new CombatRig(plugin);
        AtomicLong tick = new AtomicLong();
        SinkEnv env = freshEnv(tick::get);
        TaskHandle[] clock = new TaskHandle[1];
        clock[0] = Scheduling.repeatingGlobal(1L, 1L, tick::incrementAndGet);
        rig.onArena(world.getSpawnLocation(), sky, () -> {
            Player ringer;
            Player enemy;
            try {
                ringer = rig.spawnFake(world, "se_rf_c15_r");
                enemy = rig.spawnFake(world, "se_rf_c15_e");
            } catch (Throwable t) {
                clock[0].cancel();
                h.fail(key, t);
                rig.teardown();
                return;
            }
            // Pin the sky-arena actors in place. The bats FLY (never fall) while the clientless actors sink under
            // server-side gravity, so any elapsed time drifts the FALLING ringer down out from under the hovering
            // swarm — and the elapsed TICK count before the bell rings depends on how fast teleportAsync/chunk-load
            // completes, which lags on the older, load-heavier floor era (its run was 150+ ticks behind). That is
            // why the one-shot 12-block enumeration missed every bat there and the cloud never turned. Killing the
            // actors' gravity makes the geometry deterministic and tick-independent: ringer, enemy and swarm stay
            // co-located at Y200 regardless of era or server load, so the bell always finds the bats.
            enemy.setGravity(false);
            ringer.setGravity(false);
            // Act promptly (a short settle, NOT the full spawn-invuln wait): this check never damages the enemy,
            // so it needs no invuln window — and the bell enumerates the still-clustered swarm before it disperses.
            hop(ringer, sky.clone().add(3, 0, 0), () -> hop(enemy, sky, () ->
                    Scheduling.onEntityLater(enemy, 5L, () -> {
                        ModernDispatchSink swarmSink = new ModernDispatchSink(handles, env);
                        swarmSink.spawnSwarm(enemy.getLocation(), batId, 10, 0.5, 1.0, 600, 0.5, enemy, 16.0);
                        swarmSink.flush();
                        UUID enemyId = enemy.getUniqueId();
                        Scheduling.onEntityLater(enemy, 5L, () -> Scheduling.onEntity(ringer, () -> {
                            ModernDispatchSink bell = new ModernDispatchSink(handles, freshEnv(tick::get));
                            bell.convertSummons(ringer, 12.0, -1);
                            bell.flush();
                            awaitTurned(enemy, enemyId, tick, 0, 3, 40, ok -> {
                                h.guard(key, () -> {
                                    if (!ok) {
                                        throw new IllegalStateException(
                                                "the converted cloud never published a stable pillar at its owner");
                                    }
                                });
                                clock[0].cancel();
                                SwarmClouds.scope().clear(enemyId); // drop ONLY this test's cloud — clearAll()
                                                                    // nukes the process-global registry and would
                                                                    // vanish a concurrent suite's live cloud
                                despawnType(enemy, EntityType.BAT);
                                rig.teardown();
                            });
                        }));
                    })));
        });
    }

    /**
     * Poll the enemy's own scheduler for a turned pillar within 2 blocks of the enemy for {@code need} CONSECUTIVE
     * polls (the ADR-0068 drift lesson: never race bat AI — poll the published snapshot, not bat positions).
     */
    private void awaitTurned(Player enemy, UUID enemyId, AtomicLong tick, int elapsed, int need, int maxTicks,
                             Consumer<Boolean> done) {
        SwarmClouds.CloudTarget target = SwarmClouds.target(enemyId, tick.get());
        int streak = 0;
        if (target != null) {
            Location pos = enemy.getLocation();
            double dh = Math.hypot(target.x() - pos.getX(), target.z() - pos.getZ());
            if (dh <= 2.0) {
                streak = 1;
            }
        }
        awaitTurnedStreak(enemy, enemyId, tick, elapsed, streak, need, maxTicks, done);
    }

    private void awaitTurnedStreak(Player enemy, UUID enemyId, AtomicLong tick, int elapsed, int streak, int need,
                                   int maxTicks, Consumer<Boolean> done) {
        if (streak >= need) {
            done.accept(true);
            return;
        }
        if (elapsed >= maxTicks) {
            done.accept(false);
            return;
        }
        Scheduling.onEntityLater(enemy, 1L, () -> {
            SwarmClouds.CloudTarget target = SwarmClouds.target(enemyId, tick.get());
            int next = 0;
            if (target != null) {
                Location pos = enemy.getLocation();
                double dh = Math.hypot(target.x() - pos.getX(), target.z() - pos.getZ());
                if (dh <= 2.0) {
                    next = streak + 1;
                }
            }
            awaitTurnedStreak(enemy, enemyId, tick, elapsed + 1, next, need, maxTicks, done);
        });
    }

    // ── TRAP_BREAK ────────────────────────────────────────────────────────────────────────────────

    /** 16: Turnkey early-restores every cage tile intact, drops the ledger guard, and empties the registry. */
    private void checkTurnkeyCage(Harness h, RuntimeHandles handles, RegistryResolvers resolvers, World world,
                                  Location arena) {
        final String key = "reforge.turnkey.cageRestoredIntact";
        h.expect(key);
        int brickId = resolveId(resolvers.material("STONE_BRICKS"), "STONE_BRICKS");
        int barsId = resolveId(resolvers.material("IRON_BARS"), "IRON_BARS");
        Location base = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        SinkEnv env = freshEnv(() -> 0L);
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), base, () -> {
            Player victim;
            Player enemy;
            try {
                victim = rig.spawnFake(world, "se_rf_k16_v");
                enemy = rig.spawnFake(world, "se_rf_k16_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            Scheduling.onRegion(base, () -> {
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.cage(base, victim, enemy, brickId, barsId, brickId, 3, 4, 3, 600);
                sink.flush();
                Scheduling.onRegionLater(base, 5L, () -> {
                    List<int[]> tiles = tilesConfining(env, victim.getUniqueId(), base);
                    if (tiles.isEmpty()) {
                        h.fail(key, "cage registered no confining structure for the victim");
                        rig.teardown();
                        return;
                    }
                    ModernDispatchSink turnkey = new ModernDispatchSink(handles, env);
                    turnkey.breakTraps(victim, -1);
                    turnkey.flush();
                    Scheduling.onRegionLater(base, 8L, () -> {
                        h.guard(key, () -> assertTilesRestored(world, env, tiles));
                        rig.teardown();
                    });
                });
            });
        });
    }

    /** 17: one Turnkey restores BOTH a confined web box and a confined in-cell web (the two-structure fan-out). */
    private void checkTurnkeyBoxAndWeb(Harness h, RuntimeHandles handles, RegistryResolvers resolvers, World world,
                                       Location arena) {
        final String key = "reforge.turnkey.spiderBoxAndWebRestored";
        h.expect(key);
        int webId = resolveId(resolvers.material("COBWEB"), "COBWEB");
        Location base = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        SinkEnv env = freshEnv(() -> 0L);
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), base, () -> {
            Player victim;
            try {
                victim = rig.spawnFake(world, "se_rf_k17_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            UUID victimId = victim.getUniqueId();
            Scheduling.onRegion(base, () -> {
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.tempBox(base, webId, 3, 4, 3, 600, 2, victimId);      // the Spider box (confined)
                sink.tempBlock(base.clone().add(6, 0, 0), webId, 600, 2, false, victimId); // an in-cell web (confined)
                sink.flush();
                Scheduling.onRegionLater(base, 5L, () -> {
                    List<int[]> tiles = tilesConfining(env, victimId, base);
                    int structures = env.trapStructures().confining(victimId, world.getUID(), base.getBlockX(),
                            base.getBlockY(), base.getBlockZ(), 0L).size();
                    if (structures < 2 || tiles.isEmpty()) {
                        h.fail(key, "expected two confining structures, saw " + structures);
                        rig.teardown();
                        return;
                    }
                    ModernDispatchSink turnkey = new ModernDispatchSink(handles, env);
                    turnkey.breakTraps(victim, -1);
                    turnkey.flush();
                    Scheduling.onRegionLater(base, 8L, () -> {
                        h.guard(key, () -> {
                            assertTilesRestored(world, env, tiles);
                            if (env.trapStructures().size() != 0) {
                                throw new IllegalStateException("structures survived the break: "
                                        + env.trapStructures().size());
                            }
                        });
                        rig.teardown();
                    });
                });
            });
        });
    }

    /** 18: an UNregistered floor-paint tile (dy −1) is never confinement and stays put after a break. */
    private void checkTurnkeyFloorPaintUntouched(Harness h, RuntimeHandles handles, RegistryResolvers resolvers,
                                                 World world, Location arena) {
        final String key = "reforge.turnkey.floorPaintUntouched";
        h.expect(key);
        int netherId = resolveId(resolvers.material("NETHERRACK"), "NETHERRACK");
        Location stand = new Location(world, arena.getX(), ARENA_Y, arena.getZ());
        Location paintAt = stand.clone().add(0, -1, 0); // beneath the feet — floor paint, never registered
        SinkEnv env = freshEnv(() -> 0L);
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(world.getSpawnLocation(), stand, () -> {
            Player victim;
            try {
                victim = rig.spawnFake(world, "se_rf_k18_v");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            hop(victim, stand, () -> Scheduling.onRegion(stand, () -> {
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.tempBlock(paintAt, netherId, 600, 2, false); // 5-arg: NOT registered as confinement
                sink.flush();
                Scheduling.onRegionLater(stand, 5L, () -> {
                    ModernDispatchSink turnkey = new ModernDispatchSink(handles, env);
                    turnkey.breakTraps(victim, -1);
                    turnkey.flush();
                    Scheduling.onRegionLater(stand, 8L, () -> {
                        h.guard(key, () -> {
                            if (!env.tempBlocks().guarded(world.getUID(), paintAt.getBlockX(), paintAt.getBlockY(),
                                    paintAt.getBlockZ())) {
                                throw new IllegalStateException("Turnkey reclaimed unregistered floor paint");
                            }
                        });
                        env.tempBlocks().reclaim(world.getUID(), paintAt.getBlockX(), paintAt.getBlockY(),
                                paintAt.getBlockZ()); // clean the arena
                        rig.teardown();
                    });
                });
            }));
        });
    }

    /** 19 (Folia): a cage staged in a distinct far region restores every tile — the per-tile onRegion reclaim path. */
    private void checkTurnkeyCrossRegionCage(Harness h, RuntimeHandles handles, RegistryResolvers resolvers,
                                             World world, Location spawn) {
        final String key = "reforge.turnkey.crossRegionCageRestores";
        h.expect(key);
        if (!Capabilities.foliaPresent()) {
            plugin.getLogger().info("[reforge] single-threaded Paper — cross-region cage reclaim not applicable");
            h.pass(key);
            return;
        }
        int brickId = resolveId(resolvers.material("STONE_BRICKS"), "STONE_BRICKS");
        int barsId = resolveId(resolvers.material("IRON_BARS"), "IRON_BARS");
        // A far region (past Folia's merge radius), snapped to a 512 line so the cage sits on a region seam.
        int fx = ((spawn.getBlockX() + CrossRegion.GAP) / 512) * 512;
        int fz = ((spawn.getBlockZ() + CrossRegion.GAP) / 512) * 512;
        Location base = new Location(world, fx, ARENA_Y, fz);
        SinkEnv env = freshEnv(() -> 0L);
        CombatRig rig = new CombatRig(plugin);
        rig.onArena(spawn, base, () -> {
            Player victim;
            Player enemy;
            try {
                victim = rig.spawnFake(world, "se_rf_k19_v");
                enemy = rig.spawnFake(world, "se_rf_k19_e");
            } catch (Throwable t) {
                h.fail(key, t);
                rig.teardown();
                return;
            }
            Scheduling.onRegion(base, () -> {
                ModernDispatchSink sink = new ModernDispatchSink(handles, env);
                sink.cage(base, victim, enemy, brickId, barsId, brickId, 3, 4, 3, 600);
                sink.flush();
                Scheduling.onRegionLater(base, 5L, () -> {
                    List<int[]> tiles = tilesConfining(env, victim.getUniqueId(), base);
                    if (tiles.isEmpty()) {
                        h.fail(key, "cross-region cage registered no structure");
                        rig.teardown();
                        return;
                    }
                    ModernDispatchSink turnkey = new ModernDispatchSink(handles, env);
                    turnkey.breakTraps(victim, -1);
                    turnkey.flush();
                    Scheduling.onRegionLater(base, 10L, () -> {
                        h.guard(key, () -> assertTilesRestored(world, env, tiles));
                        rig.teardown();
                    });
                });
            });
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    /** A fresh isolated env (the ComboDotSyncSuite/FreezeSuite shape) driven by {@code nowTicks}. */
    private static SinkEnv freshEnv(java.util.function.LongSupplier nowTicks) {
        return SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(), nowTicks);
    }

    /**
     * Wire a real {@link CombatDispatch}/{@link CombatListener} + the two reforge listeners over {@code env}
     * (the strike consult marks the relay; the MONITOR listener commits; the LOWEST guard restores third-party
     * i-frames). Uncapped, unit-scaled (attack-scale 1.0) so felt-percent claims are ratios.
     */
    private void wireCombat(CombatRig rig, RuntimeHandles handles, Library library, SinkEnv env) {
        ContentHolder content = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        CombatDispatch dispatch = new CombatDispatch(executor,
                dsEnv -> new ModernDispatchSink(handles, dsEnv), Stores.probe(), content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());
        rig.listen(new CombatListener(dispatch));
        rig.listen(new ReforgeStrikeListener(env.stores(), worn, content, Messages.defaults(), Stores.sounds(),
                env.nowTicks(), () -> true, () -> true,
                feature.combat.TimingOverrideBridge.NONE));
        rig.listen(new ReforgeTempoGuardListener(env.stores().hitTempo(), env.nowTicks()));
    }

    /** Capture the committed damage of the last hit on {@code victimId} at MONITOR (event.getDamage() after the fold). */
    private void captureCommitted(CombatRig rig, UUID victimId, AtomicReference<Double> sink) {
        rig.listen(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onDamage(EntityDamageByEntityEvent e) {
                if (e.getEntity().getUniqueId().equals(victimId)) {
                    sink.set(e.getDamage());
                }
            }
        });
    }

    /** The tiles of the (first) structure confining {@code victim} near {@code at}; empty if none registered. */
    private static List<int[]> tilesConfining(SinkEnv env, UUID victim, Location at) {
        List<TrapStructures.Structure> confining = env.trapStructures().confining(victim, at.getWorld().getUID(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ(), 0L);
        List<int[]> tiles = new ArrayList<>();
        for (TrapStructures.Structure structure : confining) {
            tiles.addAll(structure.tilesSnapshot());
        }
        return tiles;
    }

    /** Every {@code tile} restored to AIR (the sky-arena original) and no longer guarded by the ledger. */
    private static void assertTilesRestored(World world, SinkEnv env, List<int[]> tiles) {
        UUID worldId = world.getUID();
        for (int[] tile : tiles) {
            Material type = world.getBlockAt(tile[0], tile[1], tile[2]).getType();
            if (type != Material.AIR) {
                throw new IllegalStateException("tile (" + tile[0] + "," + tile[1] + "," + tile[2]
                        + ") not restored to air: " + type);
            }
            if (env.tempBlocks().guarded(worldId, tile[0], tile[1], tile[2])) {
                throw new IllegalStateException("tile (" + tile[0] + "," + tile[1] + "," + tile[2]
                        + ") still guarded after the break");
            }
        }
        if (env.trapStructures().size() != 0) {
            throw new IllegalStateException("structures survived the break: " + env.trapStructures().size());
        }
    }

    /** The nearest mob of {@code type} to {@code at}, or {@code null}. */
    private static Mob findMob(World world, Location at, EntityType type) {
        for (Entity entity : world.getNearbyEntities(at, 6, 6, 6)) {
            if (entity instanceof Mob mob && mob.getType() == type) {
                return mob;
            }
        }
        return null;
    }

    /** The nearest mob of {@code type} whose custom name contains {@code nameFragment}, or {@code null}. */
    private static Mob findNamedMob(World world, Location at, EntityType type, String nameFragment) {
        for (Entity entity : world.getNearbyEntities(at, 6, 6, 6)) {
            if (entity instanceof Mob mob && mob.getType() == type) {
                String name = mob.getCustomName();
                if (name != null && name.contains(nameFragment)) {
                    return mob;
                }
            }
        }
        return null;
    }

    /** Drop a converted/spawned summon's static registry entries (best-effort per-check hygiene). */
    private static void forgetSummons(UUID entity) {
        GuardianCasts.forget(entity);
        PetSummons.forget(entity);
    }

    /** Remove every nearby entity of {@code type} around {@code anchor} (best-effort swarm cleanup). */
    private static void despawnType(Player anchor, EntityType type) {
        Scheduling.onEntity(anchor, () -> {
            for (Entity near : anchor.getNearbyEntities(30, 30, 30)) {
                if (near.getType() == type) {
                    Scheduling.onEntity(near, () -> {
                        try {
                            near.remove();
                        } catch (Throwable ignored) {
                            // best-effort
                        }
                    });
                }
            }
        });
    }

    /**
     * Folia-safe staging hop (the PetHomeSuite/BatCloudSuite idiom): teleportAsync from the player's own
     * scheduler — a synchronous teleport throws under region threading — continuing once landed plus a settle tick.
     */
    private static void hop(Player user, Location to, Runnable then) {
        Scheduling.onEntity(user, () ->
                user.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(user, 2L, then)));
    }

    /**
     * Poll each game tick until {@code cond} holds or {@code maxTicks} elapse; {@code done} is delivered on
     * {@code entity}'s scheduler (the first poll runs on the caller's thread, wrong-region after a hop).
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

    private static int resolveId(OptionalInt id, String token) {
        if (id.isEmpty()) {
            throw new IllegalStateException(token + " did not intern to a handle id on this version");
        }
        return id.getAsInt();
    }
}
