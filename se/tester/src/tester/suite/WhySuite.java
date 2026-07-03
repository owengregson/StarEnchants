package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.pipeline.GateOutcome;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.EngineStores;
import engine.stores.WhyRing;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The activation flight recorder, live (ADR-0045): the record path runs on real region threads (on Folia the
 * writer thread genuinely differs from the reading thread), which a unit test cannot exercise. Fires MINE
 * twice on a cooldown enchant and checks the ring holds an ACTIVATED then an ON_COOLDOWN row. Engine-only:
 * the renderer is unit-covered ({@code WhyRenderTest}); this decodes the raw ring, so the suite keeps no
 * {@code :bootstrap} dependency.
 */
public final class WhySuite implements Harness.Scenario {

    private static final String RECORDER = """
            display: Recorder
            applies-to: [PICKAXE]
            trigger: MINE
            levels:
              1: { chance: 100, cooldown: 100, effects: [{ POTION: { effect: REGENERATION, level: 1, duration: 40, who: "@Self" } }] }
            """;

    private final Plugin plugin;

    public WhySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("why.records.fired");
        h.expect("why.records.cooldown");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-why-suite");
            write(root, "enchants/recorder.yml", RECORDER);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("why.records.fired", "enchant failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail("why.records.fired", e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(Stores.equip(), itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);

        // Mirror the composition-root wiring: ONE EngineStores shared by the pipeline (records into why()) and
        // the sink env, so the record and its cooldown reader see the same stores.
        EngineStores stores = EngineStores.fresh();
        stores.why().generation(library.snapshot().generation());
        ActivationPipeline pipeline = new ActivationPipeline(stores.cooldowns(), SoulSpender.NONE,
                stores.suppression(), ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW, stores.why());
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                pipeline, AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = new engine.sink.SinkEnv(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, stores, tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor, dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv),
                Stores.probe(), holder, worn, triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new TriggerListeners(dispatch, Stores.hands()));

        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        codec.write(pick, new CombatState(Map.of("enchants/recorder", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player miner;
                try {
                    miner = FakePlayers.spawn(world, "se_why_miner");
                } catch (Throwable t) {
                    h.fail("why.records.fired", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(miner, () -> {
                    miner.getInventory().setItemInMainHand(pick);
                    worn.refresh(miner, library.snapshot());
                    mine(miner, at.clone().add(0, -1, 0)); // 1st MINE → ACTIVATED, arms the 100-tick cooldown
                    Scheduling.onEntityLater(miner, 5L, () -> {
                        mine(miner, at.clone().add(1, -1, 0)); // 2nd MINE → ON_COOLDOWN
                        Scheduling.onEntityLater(miner, 5L, () -> {
                            List<WhyRing.Attempt> attempts = stores.why().attempts(miner.getUniqueId());
                            h.guard("why.records.fired", () -> {
                                if (attempts.stream().noneMatch(a -> a.verdict() == GateOutcome.ACTIVATED.ordinal()
                                        && a.pA() >= 0)) {
                                    throw new IllegalStateException("no ACTIVATED record: " + attempts);
                                }
                            });
                            h.guard("why.records.cooldown", () -> {
                                if (attempts.stream().noneMatch(a -> a.verdict() == GateOutcome.ON_COOLDOWN.ordinal()
                                        && a.pB() > 0 && a.pB() <= 100)) {
                                    throw new IllegalStateException("no ON_COOLDOWN record with 0 < remaining <= 100: "
                                            + attempts);
                                }
                            });
                            FakePlayers.despawn(miner);
                            rig.teardown();
                        });
                    });
                });
            });
        });
    }

    private void mine(Player miner, Location where) {
        Block block = where.getBlock();
        block.setType(Material.STONE);
        plugin.getServer().getPluginManager().callEvent(new BlockBreakEvent(block, miner));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
