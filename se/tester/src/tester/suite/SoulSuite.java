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
import engine.stores.SoulModeStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.soul.SoulService;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.ItemKeys;
import item.codec.SoulCodec;
import item.codec.SoulData;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
 * Soul spend loop, live (§6.3): a soul-cost enchant on a weapon that is also a soul gem (mode ON) fires,
 * debits the gem, persists durably — gem PDC → toggle (seeds the shared ledger) → gate 10 spends from the
 * SAME ledger the pipeline holds → deferred PDC write. Mojang-mapped only (fake-player attacker).
 */
public final class SoulSuite implements Harness.Scenario {

    private static final String DRAINER = """
            display: Drainer
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, soul-cost: 1, effects: [{ POTION: { effect: POISON, level: 1, duration: 80, who: "@Victim" } }] }
            """;

    private final Plugin plugin;

    public SoulSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("soul.enchantFired");
        h.expect("soul.costSpentAndPersisted");
        java.util.function.Consumer<String> failBoth = msg -> {
            h.fail("soul.enchantFired", msg);
            h.fail("soul.costSpentAndPersisted", msg);
        };

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-soul-suite");
            write(root, "enchants/drainer.yml", DRAINER);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failBoth.accept("drainer failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                failBoth.accept("POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            failBoth.accept(e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        CombatCodec codec = new CombatCodec(keys.combat());
        SoulCodec soulCodec = new SoulCodec(keys.soul());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);

        // ONE ledger shared by the pipeline (gate 10) and the soul service (seed/spend).
        SoulLedger ledger = new SoulLedger();
        SoulService soulService = new SoulService(ledger, new SoulModeStore(), soulCodec,
                compile.load.SoulGemConfig::defaults);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), ledger), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                tick::incrementAndGet, soulService::bindingFor);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        // A sword that BOTH bears the drainer enchant and is a soul gem with 5 souls.
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/drainer", 1), List.of()));
        soulCodec.write(sword, new SoulData(UUID.randomUUID(), 5));

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
                    attacker = FakePlayers.spawn(world, "se_soul_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    failBoth.accept("victim/attacker spawn: " + t);
                    return;
                }
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    SoulService.Toggle toggle = soulService.toggle(attacker); // seeds the ledger from 5
                    plugin.getLogger().info("[soul-suite] soul mode toggle = " + toggle);
                    victim.damage(1.0, attacker);
                    // Poison is the COW's state → assert on the cow's own thread (Folia-correct).
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("soul.enchantFired", () -> {
                            if (toggle != SoulService.Toggle.ENABLED) {
                                throw new IllegalStateException("soul mode did not enable: " + toggle);
                            }
                            if (!victim.hasPotionEffect(poison)) {
                                throw new IllegalStateException("drainer did not fire (soul gate blocked it?)");
                            }
                        });
                        victim.remove();
                    });
                    // The soul count is the ATTACKER's item state → assert on the attacker's own thread,
                    // after the deferred persist has run (also on the attacker's thread, queued earlier).
                    Scheduling.onEntityLater(attacker, 10L, () -> {
                        h.guard("soul.costSpentAndPersisted", () -> {
                            SoulData after = soulCodec.read(attacker.getInventory().getItemInMainHand());
                            if (after == null || after.souls() != 4) {
                                throw new IllegalStateException("soul cost not spent/persisted; souls="
                                        + (after == null ? "none" : after.souls()) + " (expected 4)");
                            }
                        });
                        FakePlayers.despawn(attacker);
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
