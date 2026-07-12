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
import feature.trigger.MaxHealthDriver;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.CombatState;
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
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Worn max-health bonus, live (1.8.1 regression pin): a crystal-shape {@code HEALTH} on PASSIVE with the
 * DEFAULTED {@code @Self}, socketed on a worn helmet, must raise the player's real max health by its amount
 * through the {@link MaxHealthDriver} reconcile → dispatch → sink → attribute-modifier chain — and unequip must
 * restore the base. The one layer no offline test reaches: the real Bukkit attribute write on this version.
 */
public final class MaxHealthSuite implements Harness.Scenario {

    private static final String NATURE_SHAPE = """
            display: "&2NatureTest"
            description: [ "&2* test" ]
            applies-to: [ARMOR]
            abilities:
              - { trigger: PASSIVE, effects: [ { HEALTH: { amount: 4 } } ] }
            """;

    private final Plugin plugin;

    public MaxHealthSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("maxhealth.bonusAppliesOnEquip");
        h.expect("maxhealth.bonusRemovedOnUnequip");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-maxhealth-suite");
            Path file = root.resolve("crystals/naturetest.yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, NATURE_SHAPE, StandardCharsets.UTF_8);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("maxhealth.bonusAppliesOnEquip", "crystal failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail("maxhealth.bonusAppliesOnEquip", e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(Stores.equip(), itemViews, triggers.count(), triggers.attackTriggers(),
                        triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn, triggers,
                actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());
        MaxHealthDriver maxHealth = new MaxHealthDriver(dispatch, holder, worn,
                env.stores().suppression(), tick::incrementAndGet,
                triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));

        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        codec.write(helmet, new CombatState(Map.of(), List.of("crystals/naturetest")));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player p;
                try {
                    p = FakePlayers.spawn(world, "se_maxhealth");
                } catch (Throwable t) {
                    h.fail("maxhealth.bonusAppliesOnEquip", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(p, () -> {
                    double base = Stores.maxHealthOf(p);
                    p.getInventory().setHelmet(helmet);
                    worn.refresh(p, holder.snapshot());
                    maxHealth.refresh(p);
                    Scheduling.onEntityLater(p, 10L, () -> {
                        h.guard("maxhealth.bonusAppliesOnEquip", () -> {
                            double now = Stores.maxHealthOf(p);
                            if (Math.abs(now - (base + 4.0)) > 1e-6) {
                                throw new IllegalStateException("expected " + (base + 4.0) + " got " + now);
                            }
                        });
                        p.getInventory().setHelmet(new ItemStack(Material.AIR));
                        worn.refresh(p, holder.snapshot());
                        maxHealth.refresh(p);
                        Scheduling.onEntityLater(p, 10L, () -> {
                            h.guard("maxhealth.bonusRemovedOnUnequip", () -> {
                                double now = Stores.maxHealthOf(p);
                                if (Math.abs(now - base) > 1e-6) {
                                    throw new IllegalStateException("expected base " + base + " got " + now);
                                }
                            });
                            FakePlayers.despawn(p);
                        });
                    });
                });
            });
        });
    }
}
