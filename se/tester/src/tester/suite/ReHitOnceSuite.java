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
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
import tester.fake.FakePlayers;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * SE combat procs EXACTLY ONCE per hit (§3.7): a stronger blow landing inside the victim's invulnerability
 * window — vanilla's "damage the difference" (a crit upgrading the same swing, a faster weapon) — fires a second
 * {@code EntityDamageByEntityEvent} for the SAME hit, and SE must NOT run its enchant walk / sounds / fold again
 * (the double-sound the owner reported). Observed through a heroic sword's +200% outgoing fold: a fresh hit is
 * amplified (the fold ran), a hit while i-frames are still up is left at its base (SE skipped it). A MONITOR
 * listener reads the post-fold event damage per victim — server-side state, no client motion. Mojang-mapped only.
 */
public final class ReHitOnceSuite implements Harness.Scenario {

    private static final double HEROIC_PERCENT = 2.0; // +200% → base 1.0 folds to 3.0 when SE runs

    private final Plugin plugin;

    public ReHitOnceSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("rehit.freshHitIsAmplified");
        h.expect("rehit.reHitInsideIframesIsNotReprocessed");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);
        Library library;
        try {
            Path root = Files.createTempDirectory("se-rehit-suite");
            library = LibraryLoader.load(root, compiler, 0); // heroic is item-intrinsic — no compiled ability needed
        } catch (IOException e) {
            h.fail("rehit.freshHitIsAmplified", e);
            h.fail("rehit.reHitInsideIframesIsNotReprocessed", e);
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
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        CombatDispatch dispatch = new CombatDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> java.util.Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(dispatch));
        // Read the damage SE committed (HIGH) at MONITOR, per victim.
        AtomicReference<Double> freshDamage = new AtomicReference<>();
        AtomicReference<Double> reHitDamage = new AtomicReference<>();

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of(), List.of(), null, false, new HeroicStat(HEROIC_PERCENT, 0.0, 0.0)));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player attacker;
                LivingEntity freshCow;
                LivingEntity reHitCow;
                try {
                    attacker = rig.spawnFake(world, "se_rehit_atk");
                    freshCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                    reHitCow = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                } catch (Throwable t) {
                    h.fail("rehit.freshHitIsAmplified", "spawn: " + t);
                    h.fail("rehit.reHitInsideIframesIsNotReprocessed", "spawn: " + t);
                    return;
                }
                rig.listen(new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR)
                    public void onDamage(EntityDamageByEntityEvent e) {
                        if (e.getEntity().getUniqueId().equals(freshCow.getUniqueId())) {
                            freshDamage.set(e.getDamage());
                        } else if (e.getEntity().getUniqueId().equals(reHitCow.getUniqueId())) {
                            reHitDamage.set(e.getDamage());
                        }
                    }
                });
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());

                    freshCow.setNoDamageTicks(0);   // no i-frames → a fresh hit
                    freshCow.damage(1.0, attacker);

                    // Force the victim mid-invulnerability so this hit reads as a "damage the difference" re-hit.
                    reHitCow.setNoDamageTicks(reHitCow.getMaximumNoDamageTicks());
                    reHitCow.damage(1.0, attacker);

                    Scheduling.onEntityLater(attacker, 10L, () -> {
                        h.guard("rehit.freshHitIsAmplified", () -> {
                            Double d = freshDamage.get();
                            if (d == null || d < 1.0 + HEROIC_PERCENT - 0.5) {
                                throw new IllegalStateException("a fresh hit was NOT amplified by SE; committed="
                                        + d + " (expected ~" + (1.0 + HEROIC_PERCENT) + ")");
                            }
                        });
                        h.guard("rehit.reHitInsideIframesIsNotReprocessed", () -> {
                            Double d = reHitDamage.get();
                            if (d == null) {
                                throw new IllegalStateException("the re-hit event never fired — cannot judge the guard");
                            }
                            if (d > 1.0 + 0.5) {
                                throw new IllegalStateException("a re-hit inside i-frames was re-processed by SE "
                                        + "(the fold ran twice); committed=" + d + " (expected the base ~1.0)");
                            }
                        });
                        freshCow.remove();
                        reHitCow.remove();
                        FakePlayers.despawn(attacker);
                        rig.teardown();
                    });
                });
            });
        });
    }
}
