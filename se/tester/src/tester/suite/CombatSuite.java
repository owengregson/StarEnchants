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
 * The end-to-end combat spine, live (ADR-0014; §3.3, §3.6): a fake player wielding an enchanted weapon
 * hits a victim, and the enchant's effect lands — exercising the WHOLE runtime in one shot, on a real
 * server, on Paper and Folia. The enchant is {@code Venom} (ATTACK, {@code POTION:POISON:0:80:@Victim}),
 * so it also proves the §9 RESOLVER PAIRING: the potion handle is interned at compile time and resolved
 * back to a live {@code PotionEffectType} at runtime through the SAME retained {@code RegistryResolvers}.
 *
 * <p>Flow: compile Venom with a retained resolver → wire the executor + {@link CombatDispatch} with the
 * paired {@link RuntimeHandles} → register {@link CombatListener} → spawn a cow victim and a fake-player
 * attacker holding a Venom sword → resolve the attacker's WornState → {@code victim.damage(1, attacker)}
 * fires a real {@code EntityDamageByEntityEvent} → the listener dispatches → POTION:@Victim routes to the
 * victim's thread → the victim is poisoned. Mojang-mapped only (needs the fake-player attacker).
 */
public final class CombatSuite implements Harness.Scenario {

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:POISON:0:80:@Victim"] }
            """;

    private final Plugin plugin;

    public CombatSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("combat.enchantFiresOnHit");

        // Wire the runtime with a retained resolver so the runtime handle round-trip pairs.
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-combat-suite");
            write(root, "enchants/venom.yml", VENOM);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("combat.enchantFiresOnHit", "venom failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                h.fail("combat.enchantFiresOnHit", "POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("combat.enchantFiresOnHit", e.toString());
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
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                // Attacker = fake player (must carry a WornState). Victim = a passive, non-undead mob
                // (a cow): a normal mob takes the standard LivingEntity hurt path, so a programmatic
                // damage() fires a real EntityDamageByEntityEvent — unlike a player victim (PvP/peaceful
                // gating rejects the hit before the event) or an armour stand (custom hurt handling).
                // A cow is not undead, so POISON actually applies and is observable.
                Player attacker;
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_combat_atk");
                    victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("combat.enchantFiresOnHit", "victim/attacker spawn: " + t);
                    return;
                }
                int attackId = triggers.idOf("ATTACK").orElseThrow();
                Scheduling.onEntity(attacker, () -> {
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    WornState wornState = worn.get(attacker.getUniqueId());
                    int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                    plugin.getLogger().info("[combat-suite] venom candidates for attacker = " + candidates);
                    // Same spawn location ⇒ same region as the victim; the hit fires a real EDBE.
                    victim.damage(1.0, attacker);
                    Scheduling.onEntityLater(victim, 10L, () -> {
                        h.guard("combat.enchantFiresOnHit", () -> {
                            if (candidates <= 0) {
                                throw new IllegalStateException("no Venom candidate resolved (candidates=" + candidates
                                        + ") — worn resolve/equip issue");
                            }
                            if (!victim.hasPotionEffect(poison)) {
                                throw new IllegalStateException("candidates=" + candidates
                                        + " but victim not poisoned — EDBE/dispatch issue");
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
