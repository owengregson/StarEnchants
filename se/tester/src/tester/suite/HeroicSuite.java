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
 * Heroic percent stats, live (§6, §6.1; §F/ADR-0021): a passive {@code HeroicStat} percent, summed at equip
 * and applied as the bounded multiplicative stage on the additive damage fold, unconditionally (no
 * chance/trigger gate). Observed as a ×(1+percent) health delta on a cow. Mojang-mapped only.
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
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
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
                    victim.damage(1.0, attacker);
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("heroic.percentDamageAmplifiesHit", () -> {
                            double dealt = before - victim.getHealth();
                            // Bound the delta both ways: catches "never applied" (~1.0) and "double-applied"
                            // (~9, near-killing the 10-HP cow). A cow has no armour/resistance.
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
