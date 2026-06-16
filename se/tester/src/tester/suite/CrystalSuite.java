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
 * The crystal source, proven live end-to-end (docs/architecture.md §6.5; ADR-0014): an authored
 * crystal file compiles to a levelless ability keyed by its base key, an item carries that key in
 * its crystal LIST (not as an enchant), and the crystal fires on hit exactly like any other source.
 * This proves the {@code crystals/} reader + the {@code CombatState.crystals} → WornState union path
 * all the way to a landed effect — the new content channel, not just that it compiles. The recipe
 * mirrors {@link CombatSuite}: a fake-player attacker hits a cow; here a {@code Jolt} crystal applies
 * POISON. Mojang-mapped only (needs the fake-player attacker).
 */
public final class CrystalSuite implements Harness.Scenario {

    private static final String JOLT = """
            display: Jolt
            trigger: ATTACK
            chance: 100
            effects: ["POTION:POISON:0:80:@Victim"]
            """;

    private final Plugin plugin;

    public CrystalSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("crystal.firesOnHit");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-crystal-suite");
            write(root, "crystals/jolt.yml", JOLT);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("crystal.firesOnHit", "jolt failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                h.fail("crystal.firesOnHit", "POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("crystal.firesOnHit", e.toString());
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

        // The crystal sits in the crystal LIST, not the enchant map — the distinguishing path.
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of(), List.of("crystals/jolt")));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        int attackId = triggers.idOf("ATTACK").orElseThrow();

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player attacker;
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_crystal_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("crystal.firesOnHit", "victim/attacker spawn: " + t);
                    return;
                }
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    WornState wornState = worn.get(attacker.getUniqueId());
                    int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                    plugin.getLogger().info("[crystal-suite] jolt candidates for attacker = " + candidates);
                    victim.damage(1.0, attacker);
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("crystal.firesOnHit", () -> {
                            if (candidates <= 0) {
                                throw new IllegalStateException("no crystal candidate resolved (candidates="
                                        + candidates + ") — crystal resolve/equip issue");
                            }
                            if (!victim.hasPotionEffect(poison)) {
                                throw new IllegalStateException("candidates=" + candidates
                                        + " but victim not poisoned — crystal dispatch issue");
                            }
                        });
                        victim.remove();
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
