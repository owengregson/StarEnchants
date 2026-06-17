package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulLedger;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Heroic percent stats, proven live end-to-end (docs/architecture.md §6, §6.1; §F/ADR-0021): an item
 * carries a passive {@code HeroicStat} percent that is summed across worn/held pieces at equip time and
 * applied as the bounded MULTIPLICATIVE stage on top of the additive damage fold, UNCONDITIONALLY (no
 * chance/trigger gate). Here a fake-player attacker holds a weapon with a large heroic outgoing-damage
 * percent and NO enchants; the hit on a cow is amplified by ×(1+percent), observable as a health delta.
 * Exercises the whole heroic path: stamp HeroicStat into PDC → equip → WornResolver sums → CombatDispatch
 * contributes to the fold's heroic stage. Mojang-mapped only.
 */
public final class HeroicSuite implements Harness.Scenario {

    /** +200% outgoing — base 1.0 × (1 + 2.0) = 3.0 dealt (below the fold's ×4 heroic cap). */
    private static final double HEROIC_PERCENT = 2.0;

    private final Plugin plugin;

    public HeroicSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("heroic.percentDamageAmplifiesHit");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            // No content needed — heroic is item-intrinsic and fires with no compiled ability.
            Path root = Files.createTempDirectory("se-heroic-suite");
            library = LibraryLoader.load(root, compiler, 0);
        } catch (IOException e) {
            h.fail("heroic.percentDamageAmplifiesHit", e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        CombatDispatch dispatch = new CombatDispatch(executor, handles, holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

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
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_heroic_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("heroic.percentDamageAmplifiesHit", "victim/attacker spawn: " + t);
                    return;
                }
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    double before = victim.getHealth();
                    victim.damage(1.0, attacker); // base 1.0; heroic ×(1+HEROIC_PERCENT) → ~3.0 total
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("heroic.percentDamageAmplifiesHit", () -> {
                            double dealt = before - victim.getHealth();
                            // Base hit is 1.0; the heroic stage multiplies by (1 + 2.0) = 3.0 → ~3.0 dealt
                            // (a cow has no armour/resistance). Require the delta near the expected value —
                            // catching both "heroic never applied" (~1.0) and "heroic double-applied"
                            // (1.0 × 3.0 × 3.0 = ~9, which would near-kill the 10-HP cow).
                            double base = 1.0;
                            double expected = base * (1.0 + HEROIC_PERCENT);
                            if (dealt < expected - 0.5) {
                                throw new IllegalStateException("heroic percent damage did not amplify the hit; dealt="
                                        + dealt + " (expected ~" + expected + ")");
                            }
                            if (dealt > expected + 0.5) {
                                throw new IllegalStateException("heroic percent damage was over-applied; dealt="
                                        + dealt + " (expected ~" + expected + ")");
                            }
                        });
                        victim.remove();
                        FakePlayers.despawn(attacker);
                    });
                });
            });
        });
    }
}
