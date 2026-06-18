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
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Non-combat triggers, proven live (docs/architecture.md §3.3): the MINE trigger fires when a player
 * breaks a block, and the v3.2 BREAK trigger fires when a held item breaks — each landing the item's
 * trigger-enchant effect through {@link TriggerListeners}/{@link TriggerDispatch}. Proves the whole
 * non-combat path (Bukkit event → WornState.byTrigger → executor → Sink → world) that
 * {@code CombatDispatch} does not exercise, and that a newly-wired listener actually fires. Synthetic
 * {@code BlockBreakEvent}/{@code PlayerItemBreakEvent}s drive the registered listeners — the same path a
 * real event takes. Mojang- and spigot-mapped alike (needs the fake player).
 */
public final class TriggerSuite implements Harness.Scenario {

    private static final String EXCAVATE = """
            display: Excavate
            applies-to: [PICKAXE]
            trigger: MINE
            levels:
              1: { chance: 100, effects: ["POTION:REGENERATION:1:80:@Self"] }
            """;

    private static final String BACKLASH = """
            display: Backlash
            applies-to: [SWORD]
            trigger: BREAK
            levels:
              1: { chance: 100, effects: ["POTION:SPEED:1:80:@Self"] }
            """;

    private final Plugin plugin;

    public TriggerSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("trigger.mineFiresOnBlockBreak");
        h.expect("trigger.breakFiresOnItemBreak");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType regen;
        PotionEffectType speed;
        try {
            Path root = Files.createTempDirectory("se-trigger-suite");
            write(root, "enchants/excavate.yml", EXCAVATE);
            write(root, "enchants/backlash.yml", BACKLASH);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("trigger.mineFiresOnBlockBreak", "enchants failed to compile: " + library.diagnostics());
                return;
            }
            regen = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "REGENERATION");
            speed = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "SPEED");
            if (regen == null || speed == null) {
                h.fail("trigger.mineFiresOnBlockBreak", "REGENERATION/SPEED did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("trigger.mineFiresOnBlockBreak", e.toString());
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
        TriggerDispatch dispatch = new TriggerDispatch(executor, handles, holder, worn, triggers,
                tick::incrementAndGet, actor -> Optional.empty());
        plugin.getServer().getPluginManager().registerEvents(new TriggerListeners(dispatch), plugin);

        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        codec.write(pick, new CombatState(Map.of("enchants/excavate", 1), List.of()));
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/backlash", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player miner;
                try {
                    miner = FakePlayers.spawn(world, "se_trigger_miner");
                } catch (Throwable t) {
                    h.fail("trigger.mineFiresOnBlockBreak", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(miner, () -> {
                    miner.getInventory().setItemInMainHand(pick);
                    worn.refresh(miner, library.snapshot());
                    Block block = at.clone().add(0, -1, 0).getBlock();
                    block.setType(Material.STONE);
                    // Fire the real listener via a synthetic break of that block by the miner.
                    plugin.getServer().getPluginManager().callEvent(new BlockBreakEvent(block, miner));
                    Scheduling.onEntityLater(miner, 10L, () -> {
                        h.guard("trigger.mineFiresOnBlockBreak", () -> {
                            if (!miner.hasPotionEffect(regen)) {
                                throw new IllegalStateException("MINE enchant did not fire on block break");
                            }
                        });
                        // Now the BREAK trigger: hold the Backlash sword, re-resolve, and break it.
                        miner.getInventory().setItemInMainHand(sword);
                        worn.refresh(miner, library.snapshot());
                        plugin.getServer().getPluginManager().callEvent(new PlayerItemBreakEvent(miner, sword));
                        Scheduling.onEntityLater(miner, 10L, () -> {
                            h.guard("trigger.breakFiresOnItemBreak", () -> {
                                if (!miner.hasPotionEffect(speed)) {
                                    throw new IllegalStateException("BREAK enchant did not fire on item break");
                                }
                            });
                            FakePlayers.despawn(miner);
                        });
                    });
                });
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
