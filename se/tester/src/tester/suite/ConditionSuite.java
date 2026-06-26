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
 * The condition gate, live (§3.3, §3.4): proves the runtime FactBuffer populates so a variable-gated enchant
 * fires only when the condition holds — an unpopulated buffer throws out of gate 7 and the enchant silently
 * never fires. Cow victims (no PvP/peaceful gating) cover both a victim.health and an actor.health gate; the
 * actor-health gate also discriminates on a string victim fact. Needs the fake-player attacker.
 */
public final class ConditionSuite implements Harness.Scenario {

    private static final String LOW_STRIKE = """
            display: LowStrike
            trigger: ATTACK
            levels:
              1: { chance: 100, condition: "%victim.health% >= 8", effects: ["POTION:POISON:1:80:@Victim"] }
            """;

    // Gates on a numeric actor fact AND a string victim fact, so one hit proves both slot kinds populate.
    private static final String ACTOR_GATE = """
            display: ActorGate
            trigger: ATTACK
            levels:
              1: { chance: 100, condition: "%actor.health% >= 10 && %victim.type% == \\"COW\\"", effects: ["POTION:POISON:1:80:@Victim"] }
            """;

    private final Plugin plugin;

    public ConditionSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("condition.firesWhenMet");
        h.expect("condition.blockedWhenUnmet");
        h.expect("condition.actorAndVictimFactsFire");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType poison;
        try {
            Path root = Files.createTempDirectory("se-condition-suite");
            write(root, "enchants/lowstrike.yml", LOW_STRIKE);
            write(root, "enchants/actorgate.yml", ACTOR_GATE);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("condition.firesWhenMet", "condition enchants failed to compile: " + library.diagnostics());
                return;
            }
            poison = (PotionEffectType) handles.resolveByName(schema.spec.HandleCategory.POTION_EFFECT, "POISON");
            if (poison == null) {
                h.fail("condition.firesWhenMet", "POISON did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            h.fail("condition.firesWhenMet", e.toString());
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
        CombatDispatch dispatch = new CombatDispatch(executor, handles, holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack victimSword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(victimSword, new CombatState(Map.of("enchants/lowstrike", 1), List.of()));
        ItemStack actorSword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(actorSword, new CombatState(Map.of("enchants/actorgate", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        int attackId = triggers.idOf("ATTACK").orElseThrow();

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player victimAttacker; // wields LowStrike — gates on the cow's health
                Player actorAttacker;  // wields ActorGate — gates on its OWN health
                LivingEntity cowMet;   // full health (10) — %victim.health% >= 8 holds
                LivingEntity cowUnmet; // pre-damaged to 4 — fails
                LivingEntity cowActor; // hit by the full-health actor attacker — %actor.health% >= 10 holds
                try {
                    victimAttacker = FakePlayers.spawn(world, "se_cond_vatk");
                    actorAttacker = FakePlayers.spawn(world, "se_cond_aatk");
                    cowMet = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                    cowUnmet = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                    cowActor = (LivingEntity) world.spawnEntity(at, EntityType.COW);
                } catch (Throwable t) {
                    h.fail("condition.firesWhenMet", "spawn: " + t);
                    return;
                }

                Scheduling.onEntity(victimAttacker, () -> {
                    victimAttacker.getInventory().setItemInMainHand(victimSword);
                    worn.refresh(victimAttacker, library.snapshot());
                    WornState wornState = worn.get(victimAttacker.getUniqueId());
                    int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                    plugin.getLogger().info("[condition-suite] lowstrike candidates = " + candidates);

                    cowUnmet.setHealth(4.0);              // below the >= 8 threshold, read before the hit
                    cowMet.damage(1.0, victimAttacker);   // 10 ≥ 8 → poisoned
                    cowUnmet.damage(1.0, victimAttacker); // 4 < 8 → not poisoned

                    Scheduling.onEntityLater(cowMet, 10L, () -> {
                        h.guard("condition.firesWhenMet", () -> {
                            if (candidates <= 0) {
                                throw new IllegalStateException("no LowStrike candidate resolved (candidates="
                                        + candidates + ") — worn resolve/equip issue");
                            }
                            if (!cowMet.hasPotionEffect(poison)) {
                                throw new IllegalStateException("candidates=" + candidates
                                        + " but the full-health victim was not poisoned — facts not populated?");
                            }
                        });
                        cowMet.remove();
                        Scheduling.onEntity(cowUnmet, () -> {
                            h.guard("condition.blockedWhenUnmet", () -> {
                                if (cowUnmet.hasPotionEffect(poison)) {
                                    throw new IllegalStateException(
                                            "the low-health victim was poisoned — the condition gate did not block");
                                }
                            });
                            cowUnmet.remove();
                            FakePlayers.despawn(victimAttacker);
                        });
                    });
                });

                Scheduling.onEntity(actorAttacker, () -> {
                    actorAttacker.getInventory().setItemInMainHand(actorSword);
                    actorAttacker.setHealth(20.0); // ≥ 10 → %actor.health% holds
                    worn.refresh(actorAttacker, library.snapshot());
                    cowActor.damage(1.0, actorAttacker);
                    Scheduling.onEntityLater(cowActor, 10L, () -> {
                        h.guard("condition.actorAndVictimFactsFire", () -> {
                            if (!cowActor.hasPotionEffect(poison)) {
                                throw new IllegalStateException(
                                        "'%actor.health% >= 10 && %victim.type% == COW' did not fire — a numeric "
                                                + "actor fact or the string victim fact was not populated");
                            }
                        });
                        cowActor.remove();
                        FakePlayers.despawn(actorAttacker);
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
