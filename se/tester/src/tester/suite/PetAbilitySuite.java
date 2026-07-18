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
import engine.stores.SuppressionStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.pet.PetArmedStore;
import feature.pet.PetWornSource;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
import feature.trigger.WaterSpeedDriver;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.PetCodec;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The three ADR-0060 pet mechanisms, live: (a) a hotbar pet's FALL ability folds/cancels a real
 * environmental damage event through WornState (uncapped defense percents + CANCEL immunity), (b) the
 * PASSIVE potion + WATER_SPEED worn channels maintain Water Breathing and the water-movement attribute
 * modifier (1.21+; passed-with-note where the attribute is absent), and (c) SPAWN_SWARM spawns a ring of
 * vanilla-AI summons at Y-scattered chest height (ADR-0068) that the TTL removes. Test-owned defs; clientless fake player
 * (players do not fall server-side, so the FALL event is synthesized — the PlayerItemDamageEvent rule).
 */
@SuppressWarnings("deprecation") // EntityDamageEvent(Entity,DamageCause,double): the floor-stable synthetic-fall ctor (A13), deprecated-not-removed across the range
public final class PetAbilitySuite implements Harness.Scenario {

    private static final String SHEEP = """
            display: Sheep
            type: PASSIVE
            levels:
              1:   { trigger: FALL, effects: [ { DAMAGE_MOD: { side: defense, mode: add, amount: 25 } } ] }
              100: { trigger: FALL, effects: [ { CANCEL: {} } ] }
            """;

    private static final String KRAKEN = """
            display: Kraken
            type: PASSIVE
            levels:
              1: { effects: [ { POTION: { effect: "WATER_BREATHING", level: 1, duration: 200, who: "@Self" } }, { WATER_SPEED: { efficiency: 0.09 } } ] }
            """;

    private static final String BAT = """
            display: Bat
            type: ACTIVE
            levels:
              1: { cooldown: 0, effects: [ { SPAWN_SWARM: { type: BAT, count: 12, radius: 0.5, ttl: 60, speed: 0.5 } } ] }
            """;

    private static final String FALL_REDUCED = "pets.sheepFallReduced";
    private static final String FALL_CANCELLED = "pets.sheepMaxLevelFallCancelled";
    private static final String BREATHING = "pets.krakenWaterBreathingMaintained";
    private static final String WATER_SPEED = "pets.krakenWaterSpeedModifier";
    private static final String SWARM_RING = "pets.batSwarmRingSpawns";
    private static final String SWARM_JITTER = "pets.batSwarmSpawnYJitterSpreads";
    private static final String SWARM_TTL = "pets.batSwarmTtlRemoves";

    private final Plugin plugin;

    public PetAbilitySuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(FALL_REDUCED);
        h.expect(FALL_CANCELLED);
        h.expect(BREATHING);
        h.expect(WATER_SPEED);
        h.expect(SWARM_RING);
        h.expect(SWARM_JITTER);
        h.expect(SWARM_TTL);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        PotionEffectType waterBreathing;
        try {
            Path root = Files.createTempDirectory("se-pet-ability-suite");
            write(root, "pets/sheep.yml", SHEEP);
            write(root, "pets/kraken.yml", KRAKEN);
            write(root, "pets/bat.yml", BAT);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "pets failed to compile: " + library.diagnostics());
                return;
            }
            waterBreathing = (PotionEffectType) handles.resolveByName(HandleCategory.POTION_EFFECT,
                    "WATER_BREATHING");
            if (waterBreathing == null) {
                failAll(h, "WATER_BREATHING did not resolve on this version");
                return;
            }
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        ItemKeys keys = ItemKeys.of();
        PetCodec petCodec = new PetCodec(keys, Stores.state());
        CombatCodec combat = new CombatCodec(keys.combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(combat, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        AtomicLong tick = new AtomicLong();
        PetArmedStore armed = new PetArmedStore();
        PetWornSource petSource = new PetWornSource(() -> true, petCodec, holder::library, armed, tick::get);
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews,
                triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers(),
                () -> WornResolver.Features.ALL, Set::of, petSource)::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());
        int held = triggers.idOf("HELD").orElse(-1);
        int passive = triggers.idOf("PASSIVE").orElse(-1);
        PassiveEffectDriver passives = new PassiveEffectDriver(dispatch, holder, worn,
                new SuppressionStore(), tick::get, held, passive);
        WaterSpeedDriver waterSpeed = new WaterSpeedDriver(dispatch, holder, worn,
                new SuppressionStore(), tick::get, held, passive);

        CombatRig rig = new CombatRig(plugin);
        rig.listen(new TriggerListeners(dispatch, Stores.hands()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        rig.onArena(at, () -> {
            Player user;
            try {
                user = rig.spawnFake(world, "se_petability");
            } catch (Throwable t) {
                failAll(h, "fake-player spawn: " + t);
                return;
            }
            Scheduling.onEntity(user, () -> {
                // (a) Sheep level 1: −25% fall damage, TRUE percent (env fold is uncapped/unscaled).
                ItemStack sheep = new ItemStack(Material.PAPER);
                petCodec.stamp(sheep, "sheep", 1);
                user.getInventory().setItem(0, sheep);
                worn.refresh(user, holder.snapshot());
                EntityDamageEvent fall = new EntityDamageEvent(user,
                        EntityDamageEvent.DamageCause.FALL, 10.0);
                plugin.getServer().getPluginManager().callEvent(fall);
                h.guard(FALL_REDUCED, () -> {
                    if (fall.isCancelled() || Math.abs(fall.getDamage() - 7.5) > 1e-6) {
                        throw new IllegalStateException("level-1 sheep fold: expected 7.5 uncancelled, got "
                                + fall.getDamage() + " cancelled=" + fall.isCancelled());
                    }
                });

                // (a') Sheep level 100: CANCEL = true immunity.
                ItemStack maxSheep = new ItemStack(Material.PAPER);
                petCodec.stamp(maxSheep, "sheep", 100);
                user.getInventory().setItem(0, maxSheep);
                worn.refresh(user, holder.snapshot());
                EntityDamageEvent bigFall = new EntityDamageEvent(user,
                        EntityDamageEvent.DamageCause.FALL, 19.0);
                plugin.getServer().getPluginManager().callEvent(bigFall);
                h.guard(FALL_CANCELLED, () -> {
                    if (!bigFall.isCancelled()) {
                        throw new IllegalStateException("level-100 sheep did not cancel the fall event");
                    }
                });

                // (b) Kraken: maintained Water Breathing + the water-speed attribute modifier.
                ItemStack kraken = new ItemStack(Material.PAPER);
                petCodec.stamp(kraken, "kraken", 1);
                user.getInventory().setItem(1, kraken);
                worn.refresh(user, holder.snapshot());
                passives.refresh(user);
                waterSpeed.refresh(user);
                Scheduling.onEntityLater(user, 4L, () -> {
                    h.guard(BREATHING, () -> {
                        if (!user.hasPotionEffect(waterBreathing)) {
                            throw new IllegalStateException("kraken did not maintain Water Breathing");
                        }
                    });
                    Object resolved = handles.resolveByName(HandleCategory.ATTRIBUTE,
                            "GENERIC_WATER_MOVEMENT_EFFICIENCY");
                    if (resolved instanceof Attribute attribute) {
                        h.guard(WATER_SPEED, () -> {
                            AttributeInstance instance = user.getAttribute(attribute);
                            double value = instance == null ? Double.NaN : instance.getValue();
                            if (instance == null || Math.abs(value - 0.09) > 1e-9) {
                                throw new IllegalStateException(
                                        "water_movement_efficiency expected 0.09, got " + value);
                            }
                        });
                    } else {
                        h.pass(WATER_SPEED); // pre-1.21: the ADR-0060 recorded degrade — nothing to assert
                    }

                    // (c) Bat: fire the live bracket's USE keys straight through the shared spine.
                    List<String> useKeys = holder.library().petDefOf("bat").bracketFor(1).useStableKeys();
                    dispatch.fireUse(user, useKeys);
                    // ADR-0068 spawn-Y jitter: captured EARLY (2 ticks), before AI drift widens the band —
                    // [0.45, 1.95] = rise 1.2 ± jitter 0.6 ± 0.15 drift; a near-equal spread means the
                    // scatter never applied (P(span < 0.15 | 12 uniform rolls) ≈ 2e-10 — flake-proof).
                    Scheduling.onEntityLater(user, 2L, () -> {
                        h.guard(SWARM_JITTER, () -> {
                            double min = Double.POSITIVE_INFINITY;
                            double max = Double.NEGATIVE_INFINITY;
                            int seen = 0;
                            for (Entity near : user.getNearbyEntities(5, 5, 5)) {
                                if (near.getType() == EntityType.BAT) {
                                    double dy = near.getLocation().getY() - user.getLocation().getY();
                                    if (dy < 1.2 - 0.75 || dy > 1.2 + 0.75) {
                                        throw new IllegalStateException(
                                                "a swarm bat spawned outside the jitter band: dy=" + dy);
                                    }
                                    min = Math.min(min, dy);
                                    max = Math.max(max, dy);
                                    seen++;
                                }
                            }
                            if (seen == 0) {
                                throw new IllegalStateException("no swarm bats present 2 ticks after the summon");
                            }
                            if (max - min < 0.15) {
                                throw new IllegalStateException("spawn heights did not scatter: span="
                                        + (max - min) + " over " + seen + " bats");
                            }
                        });
                        Scheduling.onEntityLater(user, 5L, () -> {
                            h.guard(SWARM_RING, () -> {
                                int bats = 0;
                                for (Entity near : user.getNearbyEntities(5, 5, 5)) {
                                    if (near.getType() == EntityType.BAT) {
                                        bats++;
                                        double dy = near.getLocation().getY() - user.getLocation().getY();
                                        if (dy < 0.0 || dy > 2.6) {
                                            throw new IllegalStateException(
                                                    "a swarm bat spawned outside chest height: dy=" + dy);
                                        }
                                    }
                                }
                                if (bats != 12) {
                                    throw new IllegalStateException("expected 12 swarm bats, found " + bats);
                                }
                            });
                            Scheduling.onEntityLater(user, 75L, () -> { // ttl 60 + margin
                                h.guard(SWARM_TTL, () -> {
                                    for (Entity near : user.getNearbyEntities(8, 8, 8)) {
                                        if (near.getType() == EntityType.BAT) {
                                            throw new IllegalStateException("a swarm bat outlived its TTL");
                                        }
                                    }
                                });
                                rig.teardown();
                            });
                        });
                    });
                });
            });
        });
    }

    private void failAll(Harness h, String message) {
        h.fail(FALL_REDUCED, message);
        h.fail(FALL_CANCELLED, message);
        h.fail(BREATHING, message);
        h.fail(WATER_SPEED, message);
        h.fail(SWARM_RING, message);
        h.fail(SWARM_JITTER, message);
        h.fail(SWARM_TTL, message);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
