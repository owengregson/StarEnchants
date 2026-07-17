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
import engine.stores.CooldownStore;
import engine.stores.RageStackStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.RageStacksListener;
import feature.combat.RageStacksService;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * SE combat procs EXACTLY ONCE per hit (§3.7) — keyed on HIT IDENTITY, not the victim's shared i-frame
 * window. Five contracts against real vanilla "damage the difference" events, observed through a heroic
 * sword's +200% outgoing fold (a LOWEST listener reads the damage as vanilla reported it, a MONITOR
 * listener reads what SE committed — server-side state, no client motion):
 * (1) a fresh hit is amplified; (2) the SAME attacker's re-hit inside the window their own landed hit
 * opened (a crit upgrade) is NOT re-processed; (3) a DISTINCT second attacker inside that window still
 * procs; (4) a window nobody stamped (how fire/poison/DoT ticks arm it, ADR-0054) still procs; (5) rage
 * advances exactly once per swing (the MONITOR relay, ADR-0058 economy). Mojang-mapped only.
 */
public final class ReHitOnceSuite implements Harness.Scenario {

    private static final double HEROIC_PERCENT = 2.0; // +200% → the fold triples the event damage when SE runs
    private static final int RAGE_LEVEL = 3;          // test-owned level; caps the run above the asserted stacks

    private static final String FRESH = "rehit.freshHitIsAmplified";
    private static final String CRIT = "rehit.critUpgradeReHitIsNotReprocessed";
    private static final String GANK = "rehit.distinctAttackerInsideWindowStillProcs";
    private static final String WINDOW = "rehit.unstampedWindowStillProcs";
    private static final String RAGE = "rehit.rageAdvancesOncePerSwing";
    private static final List<String> CHECKS = List.of(FRESH, CRIT, GANK, WINDOW, RAGE);

    private final Plugin plugin;

    public ReHitOnceSuite(Plugin plugin) {
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
            Path root = Files.createTempDirectory("se-rehit-suite");
            library = LibraryLoader.load(root, compiler, 0); // heroic is item-intrinsic — no compiled ability needed
        } catch (IOException e) {
            failAll(h, e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        // A CONSTANT tick (tick::get, never incrementAndGet): the guard's same-hit horizon is ~10 ticks and
        // must not be eaten by per-read increments; nothing staged here needs an advancing clock (the combo
        // and gank windows are all >= 100 ticks).
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::get);
        CombatDispatch dispatch = new CombatDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> java.util.Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());
        RageStackStore rageStacks = env.stores().rageStacks();
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(dispatch));

        // Per-victim event capture: LOWEST = the damage as vanilla reported it (for a re-hit, the DIFFERENCE
        // chunk), MONITOR = what SE committed. Staging is synchronous, so each check reads the LAST event
        // pair on its own dedicated victim.
        Map<UUID, Double> raw = new ConcurrentHashMap<>();
        Map<UUID, Double> committed = new ConcurrentHashMap<>();

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of(), List.of(), null, false, new HeroicStat(HEROIC_PERCENT, 0.0, 0.0)));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        rig.onArena(at, () -> {
            Player attacker;
            Player second;
            LivingEntity freshCow;
            LivingEntity critCow;
            LivingEntity gankCow;
            LivingEntity windowCow;
            LivingEntity rageCow;
            try {
                attacker = rig.spawnFake(world, "se_rehit_a1");
                second = rig.spawnFake(world, "se_rehit_a2");
                freshCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                critCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                gankCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                windowCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                rageCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
            } catch (Throwable t) {
                failAll(h, t);
                return;
            }
            rig.listen(new Listener() {
                @EventHandler(priority = EventPriority.LOWEST)
                public void pre(EntityDamageByEntityEvent e) {
                    raw.put(e.getEntity().getUniqueId(), e.getDamage());
                }

                @EventHandler(priority = EventPriority.MONITOR)
                public void post(EntityDamageByEntityEvent e) {
                    committed.put(e.getEntity().getUniqueId(), e.getDamage());
                }
            });
            // Rage is wired ONLY for our attacker, so concurrent suites' hits stay out of the run (MONITOR
            // listeners hear every suite's events).
            UUID rager = attacker.getUniqueId();
            RageStacksService rage = new RageStacksService(p -> p.getUniqueId().equals(rager) ? RAGE_LEVEL : 0,
                    env.stores().combo(), rageStacks, platform.lang.Messages.defaults(), Stores.sounds(),
                    tick::get);
            rig.listen(new RageStacksListener(rage));

            AtomicInteger stacksAfterDuplicate = new AtomicInteger(-1);
            AtomicInteger stacksAfterSecondSwing = new AtomicInteger(-1);
            Scheduling.onEntity(attacker, () -> {
                attacker.getInventory().setItemInMainHand(sword);
                worn.refresh(attacker, library.snapshot());
                second.getInventory().setItemInMainHand(sword);
                worn.refresh(second, library.snapshot());

                // (1) A fresh hit (no i-frames) runs the fold: 1.0 → ~3.0.
                freshCow.setNoDamageTicks(0);
                freshCow.damage(1.0, attacker);

                // (2) The SAME attacker again inside the window their own landed hit opened: vanilla fires
                // the "damage the difference" event for the SAME swing (a crit upgrade) — SE must not re-fold.
                // 10.0 clears lastHurt whichever space vanilla stored it in (raw 1.0 or committed 3.0).
                critCow.setNoDamageTicks(0);
                critCow.damage(1.0, attacker);
                critCow.damage(10.0, attacker);

                // (3) A DISTINCT attacker inside the same kind of window is a REAL hit — full walk + fold.
                gankCow.setNoDamageTicks(0);
                gankCow.damage(1.0, attacker);
                gankCow.damage(10.0, second);

                // (4) A window nobody stamped (how fire/poison/DoT ticks arm it, ADR-0054): still a real hit.
                windowCow.setNoDamageTicks(windowCow.getMaximumNoDamageTicks());
                windowCow.damage(1.0, attacker);

                // (5) Rage: one swing = one advance. Small amounts keep the cow alive across three events
                // (0.5 folds to ~1.5; the duplicate's difference chunk is <= 1.5).
                rageCow.setNoDamageTicks(0);
                rageCow.damage(0.5, attacker);   // swing 1 → stacks 1
                rageCow.damage(2.0, attacker);   // the same swing's difference event → stacks must stay 1
                stacksAfterDuplicate.set(rageStacks.current(rager));
                rageCow.setNoDamageTicks(0);     // leave the window → the next hit is a real second swing
                rageCow.damage(0.5, attacker);   // swing 2 → stacks 2 (the guard must not over-suppress)
                stacksAfterSecondSwing.set(rageStacks.current(rager));

                Scheduling.onEntityLater(attacker, 10L, () -> {
                    h.guard(FRESH, () -> assertFolded("a fresh hit", raw.get(freshCow.getUniqueId()),
                            committed.get(freshCow.getUniqueId())));
                    h.guard(CRIT, () -> {
                        Double r = raw.get(critCow.getUniqueId());
                        Double c = committed.get(critCow.getUniqueId());
                        requireDifferenceEvent("crit-upgrade", r, c);
                        if (c > r * 1.5) {
                            throw new IllegalStateException("a same-attacker re-hit was RE-processed:"
                                    + " committed=" + c + " for a difference chunk of " + r);
                        }
                    });
                    h.guard(GANK, () -> {
                        Double r = raw.get(gankCow.getUniqueId());
                        Double c = committed.get(gankCow.getUniqueId());
                        requireDifferenceEvent("second-attacker", r, c);
                        if (c < r * 2.0) {
                            throw new IllegalStateException("a DISTINCT attacker inside the window was"
                                    + " dropped: committed=" + c + " for a difference chunk of " + r);
                        }
                    });
                    h.guard(WINDOW, () -> assertFolded("a hit inside an unstamped window",
                            raw.get(windowCow.getUniqueId()), committed.get(windowCow.getUniqueId())));
                    h.guard(RAGE, () -> {
                        if (stacksAfterDuplicate.get() != 1) {
                            throw new IllegalStateException("one swing advanced rage to "
                                    + stacksAfterDuplicate.get() + " (the crit-upgrade double-advance)");
                        }
                        if (stacksAfterSecondSwing.get() != 2) {
                            throw new IllegalStateException("a real second swing advanced rage to "
                                    + stacksAfterSecondSwing.get() + " instead of 2 (over-suppressed)");
                        }
                    });
                    rig.teardown();
                });
            });
        });
    }

    /** The fold ran: committed ≈ raw × (1 + HEROIC_PERCENT); anything under 2× reads as "SE skipped it". */
    private static void assertFolded(String what, Double raw, Double committed) {
        if (raw == null || committed == null) {
            throw new IllegalStateException(what + ": no event captured; raw=" + raw + " committed=" + committed);
        }
        if (committed < raw * 2.0) {
            throw new IllegalStateException(what + " was NOT amplified by SE; raw=" + raw + " committed="
                    + committed + " (expected ~" + (raw * (1.0 + HEROIC_PERCENT)) + ")");
        }
    }

    /** The staged second hit's difference event fired (raw > the 1.0 opener) and was observed at MONITOR. */
    private static void requireDifferenceEvent(String what, Double raw, Double committed) {
        if (raw == null || committed == null) {
            throw new IllegalStateException(what + ": no event captured; raw=" + raw + " committed=" + committed);
        }
        if (raw < 3.5) {
            throw new IllegalStateException(what + ": the difference event never fired (raw=" + raw
                    + " is still the opening swing) — cannot judge the guard");
        }
    }

    private void failAll(Harness h, Throwable t) {
        for (String check : CHECKS) {
            h.fail(check, t);
        }
    }
}
