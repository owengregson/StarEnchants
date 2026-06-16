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
import platform.protect.ProtectionProvider;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * The gate-2 protection seam, live (docs/architecture.md §2, §3.3): the SAME Venom ATTACK enchant is
 * blocked at a "protected" block and allowed one block over, driven by a {@link ProtectionService}
 * with a fake {@link ProtectionProvider} that denies a single block. This proves the whole protection
 * chain end-to-end on a real Paper + Folia server — the firing {@code Location} is captured on the
 * {@code Activation}, the gate-2 {@code Guard} consults the composed service, and a deny stops the
 * effect from ever landing (no poison) while an allow lets it through (poison) — without needing a
 * real land-protection plugin (none exist on the matrix). Mojang-mapped only (needs the fake attacker).
 */
public final class ProtectionSuite implements Harness.Scenario {

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:POISON:0:80:@Victim"] }
            """;

    private final Plugin plugin;

    public ProtectionSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("protection.blockedInProtectedRegion");
        h.expect("protection.allowedOutsideProtectedRegion");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-protection-suite");
            write(root, "enchants/venom.yml", VENOM);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("protection.blockedInProtectedRegion", "venom failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                h.fail("protection.blockedInProtectedRegion", "POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("protection.blockedInProtectedRegion", e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of(plugin).combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AtomicLong tick = new AtomicLong();

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        // The protected block is where the FIRST victim stands; the second victim stands a few blocks
        // over (same chunk, so both load together). The provider denies exactly the protected block.
        Location protectedAt = at.clone();
        Location allowedAt = at.clone().add(4, 0, 0);
        int protX = protectedAt.getBlockX();
        int protZ = protectedAt.getBlockZ();

        ProtectionProvider denyProtectedBlock = (actor, where) ->
                !(where.getBlockX() == protX && where.getBlockZ() == protZ);
        ProtectionService protection = new ProtectionService(List.of(denyProtectedBlock));
        // Identical shape to the bootstrap's gate-2 guard (UUID-based, reads the firing location) — so
        // this exercises the real composition-root wiring, not a test-only shortcut.
        ActivationPipeline.Guard guard = (ability, activation) -> {
            Location where = activation.location();
            return where == null || protection.allows(activation.actor(), where);
        };

        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger(), guard, ActivationPipeline.Guard.ALLOW),
                AreaScan.NONE);
        CombatDispatch dispatch = new CombatDispatch(executor, handles, holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));

        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            world.setChunkForceLoaded(allowedAt.getBlockX() >> 4, allowedAt.getBlockZ() >> 4, true);
            Scheduling.onRegion(at, () -> {
                Player attacker;
                LivingEntity protectedVictim;
                LivingEntity allowedVictim;
                try {
                    attacker = FakePlayers.spawn(world, "se_prot_atk");
                    protectedVictim = (LivingEntity) world.spawnEntity(protectedAt, EntityType.COW);
                    allowedVictim = (LivingEntity) world.spawnEntity(allowedAt, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("protection.blockedInProtectedRegion", "spawn: " + t);
                    return;
                }
                int attackId = triggers.idOf("ATTACK").orElseThrow();
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    var wornState = worn.get(attacker.getUniqueId()); // log-only sanity on candidate resolution
                    plugin.getLogger().info("[protection-suite] venom candidates = "
                            + (wornState == null ? -1 : wornState.byTrigger(attackId).length));
                    // Same region as both victims (spawn chunk) — each hit fires a real EDBE.
                    protectedVictim.damage(1.0, attacker);
                    allowedVictim.damage(1.0, attacker);
                    Scheduling.onEntityLater(allowedVictim, 10L, () -> {
                        h.guard("protection.blockedInProtectedRegion", () -> {
                            if (protectedVictim.hasPotionEffect(poison)) {
                                throw new IllegalStateException(
                                        "victim in the protected block was poisoned — gate 2 did not block");
                            }
                        });
                        h.guard("protection.allowedOutsideProtectedRegion", () -> {
                            if (!allowedVictim.hasPotionEffect(poison)) {
                                throw new IllegalStateException(
                                        "victim outside the protected block was NOT poisoned — gate 2 over-blocked");
                            }
                        });
                        protectedVictim.remove();
                        allowedVictim.remove();
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
