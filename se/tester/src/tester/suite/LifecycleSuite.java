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
import feature.trigger.LifecycleDriver;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornState;
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
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * §B equipment-lifecycle + COMMAND trigger, live (§B, ADR-0022): the start/stop lifecycle the engine
 * otherwise lacks, through {@link LifecycleDriver}/{@link TriggerDispatch} — including the STOP half (removal
 * on unequip, not fire-and-forget on equip).
 */
public final class LifecycleSuite implements Harness.Scenario {

    private static final String VIGOR = """
            display: Vigor
            applies-to: [SWORD]
            trigger: HELD
            levels:
              1: { chance: 100, effects: ["POTION:SPEED:1:1000000:@Self"] }
            """;

    private static final String SURGE = """
            display: Surge
            applies-to: [SWORD]
            trigger: COMMAND
            levels:
              1: { chance: 100, effects: ["POTION:REGENERATION:1:200:@Self"] }
            """;

    private final Plugin plugin;

    public LifecycleSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("lifecycle.heldBuffStartsOnEquip");
        h.expect("lifecycle.heldBuffStopsOnUnequip");
        h.expect("lifecycle.commandFires");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType speed;
        PotionEffectType regen;
        try {
            Path root = Files.createTempDirectory("se-lifecycle-suite");
            write(root, "enchants/vigor.yml", VIGOR);
            write(root, "enchants/surge.yml", SURGE);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("lifecycle.heldBuffStartsOnEquip", "enchants failed to compile: " + library.diagnostics());
                return;
            }
            speed = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "SPEED");
            regen = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "REGENERATION");
            if (speed == null || regen == null) {
                h.fail("lifecycle.heldBuffStartsOnEquip", "SPEED/REGENERATION did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("lifecycle.heldBuffStartsOnEquip", e.toString());
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
        TriggerDispatch dispatch = new TriggerDispatch(executor, handles, holder, worn, triggers,
                tick::incrementAndGet, actor -> Optional.empty());
        LifecycleDriver lifecycle = new LifecycleDriver(dispatch, holder,
                triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));

        ItemStack vigorSword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(vigorSword, new CombatState(Map.of("enchants/vigor", 1), List.of()));
        ItemStack surgeSword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(surgeSword, new CombatState(Map.of("enchants/surge", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player p;
                try {
                    p = FakePlayers.spawn(world, "se_lifecycle");
                } catch (Throwable t) {
                    h.fail("lifecycle.heldBuffStartsOnEquip", "fake-player spawn: " + t);
                    return;
                }
                Scheduling.onEntity(p, () -> {
                    p.getInventory().setItemInMainHand(vigorSword);
                    lifecycle.refresh(p, worn.refresh(p, holder.snapshot()));
                    Scheduling.onEntityLater(p, 10L, () -> {
                        h.guard("lifecycle.heldBuffStartsOnEquip", () -> {
                            if (!p.hasPotionEffect(speed)) {
                                throw new IllegalStateException("HELD buff did not apply on equip");
                            }
                        });
                        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                        lifecycle.refresh(p, worn.refresh(p, holder.snapshot()));
                        Scheduling.onEntityLater(p, 10L, () -> {
                            h.guard("lifecycle.heldBuffStopsOnUnequip", () -> {
                                if (p.hasPotionEffect(speed)) {
                                    throw new IllegalStateException("HELD buff was not removed on unequip");
                                }
                            });
                            p.getInventory().setItemInMainHand(surgeSword);
                            worn.refresh(p, holder.snapshot());
                            dispatch.fireCommand(p);
                            Scheduling.onEntityLater(p, 10L, () -> {
                                h.guard("lifecycle.commandFires", () -> {
                                    if (!p.hasPotionEffect(regen)) {
                                        throw new IllegalStateException("COMMAND enchant did not fire on /cast");
                                    }
                                });
                                FakePlayers.despawn(p);
                            });
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
