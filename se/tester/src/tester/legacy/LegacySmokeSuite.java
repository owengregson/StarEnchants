package tester.legacy;

import api.event.EnchantActivateEvent;
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
import engine.stores.KnockbackControlStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.KnockbackListener;
import feature.menu.EnchantMenu;
import feature.menu.MenuHolder;
import feature.menu.MenuListener;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.ItemKeys;
import item.mint.ItemFactory;
import item.render.LoreRenderer;
import item.render.LoreStyle;
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
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import platform.caps.Capabilities;
import platform.item.ItemGroups;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * The reduced smoke suite for the OPTIONAL 1.8.9 lane (docs/legacy-1.8.9-codeshare-design.md §11). The full
 * {@link tester.SeTesterPlugin} harness starts on {@code ServerLoadEvent} and registers ~38 modern-API suites,
 * many of which reference modern-only seams ({@code RuntimeHandles}, the {@code RuntimeHandles}-arg sink
 * factory) that do not exist in the legacy overlay — so neither the entry nor those suites even COMPILE against
 * craftbukkit-1.8.8. The legacy build (tester/build.gradle.kts) therefore compiles ONLY this package, driven by
 * {@link LegacySmokePlugin} instead, and the legacy seams are used directly here (the legacy
 * {@code LegacyDispatchSink} takes a {@code RenameResolvers}; potions resolve via {@code PotionEffectType}).
 *
 * <p>Every check is written 1.8-safe by hand (the tester compiles against the floor, not dual-compiled against
 * 1.8.8, so javac is NOT the net here — the live boot is): {@code getItemInHand}, never {@code getItemInMainHand};
 * no {@code World#setChunkForceLoaded} (1.13+); no off-hand / {@code Particle} / {@code Attribute} types. The
 * item checks need no player; the combat + GUI checks exercise the v1_8_R3 fake-player path (the one genuinely
 * risky NMS edge — ADR-0018), so a spawn failure isolates to those two while the item checks still report.
 *
 * <p>It drives the REAL production code (compiler, {@link ItemEnchanter}, {@link CombatDispatch}, the menus,
 * {@link ItemFactory}) so the legacy overlays are what runs. Coverage is deliberately curated, not exhaustive:
 * the combat check transitively exercises the {@code Sink} potion path, so there is no separate per-family sink
 * check; the modern matrix (run-matrix.sh) remains the exhaustive gate for 1.17.1 → 26.1.x.
 */
public final class LegacySmokeSuite implements Harness.Scenario {

    private static final String KEEN = """
            display: "&bKeen"
            applies-to: [SWORD]
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 1 } }] }
              2: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 2 } }] }
            """;

    // §6.6 set with NETHERITE armour: on 1.8 those materials do not exist, so minting must DEGRADE them to the
    // DIAMOND equivalents (the item-2 fix, ItemFactory#LEGACY_FALLBACK) rather than drop to the generic fallback.
    private static final String VANGUARD = """
            display: "&bVanguard"
            complete: 4
            armor:
              pieces:
                helmet:     { material: NETHERITE_HELMET,     name: "&bVanguard Helm" }
                chestplate: { material: NETHERITE_CHESTPLATE, name: "&bVanguard Chestplate" }
                leggings:   { material: NETHERITE_LEGGINGS,   name: "&bVanguard Leggings" }
                boots:      { material: NETHERITE_BOOTS,      name: "&bVanguard Boots" }
            bonuses:
              - on: armor
                trigger: DEFENSE
                effects: [{ MODIFY_HEALTH: { amount: 1 } }]
            """;

    private static final String VENOM = """
            display: Venom
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ POTION: { effect: POISON, level: 1, duration: 80, who: "@Victim" } }] }
            """;

    private final Plugin plugin;

    public LegacySmokeSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        itemChecks(h);
        combatCheck(h);
        guiCheck(h);
        degradeChecks(h);
        gearPollChecks(h);
        projectileLandChecks(h);
        maxHealthCheck(h);
        overhealthChecks(h);
    }

    // ── overhealth drain + potion lock (ADR-0057): the 1.8 NMS modifier path + the re-strip-only lock backstop ──

    /**
     * The 1.8.9 counterpart to the modern {@code OverhealthDrainSuite}: the same two just-changed {@code Sink}
     * methods over the {@code v1_8_R3} NMS path, where the modern seams are absent.
     *
     * <ul>
     *   <li><strong>drain over HEALTH_BOOST</strong> — a real HEALTH_BOOST potion raises the NMS max-health
     *       attribute VALUE (a modifier, not a base shift); {@code drainMaxHealth(1.0)} must take that overhealth
     *       to the base for the window via {@code LegacyDispatchSink.addMaxHealthModifier} (NMS {@code b()}/{@code c()}
     *       — the negative counter-modifier), then restore the boosted max after it. Reads the NMS attribute value
     *       directly ({@link #nmsMaxHealth}) — the 1.8 game truth.</li>
     *   <li><strong>potion-lock denial</strong> — with no {@code EntityPotionEffectEvent} on 1.8, the Sink's per-tick
     *       re-strip is the SOLE enforcer: a re-application during the window must be stripped away (denied), and a
     *       fresh one after the window must stick (the counter-proof).</li>
     * </ul>
     */
    @SuppressWarnings("deprecation") // PotionEffectType.getByName / hasPotionEffect(PotionEffectType): the 1.8-present API
    private void overhealthChecks(Harness h) {
        h.expect("legacy.overhealth.drainDuring");
        h.expect("legacy.overhealth.drainRestored");
        h.expect("legacy.overhealth.lockDenied");
        h.expect("legacy.overhealth.lockReallowed");

        // The legacy sink resolves an interned potion id back through the SAME RegistryResolvers used to intern it
        // (LegacyDispatchSink.potionEffect(int) → resolvers.nameOf → PotionEffectType.getByName), so pair them.
        RegistryResolvers resolvers = new RegistryResolvers();
        PotionEffectType healthBoost = PotionEffectType.getByName("HEALTH_BOOST");
        OptionalInt healthBoostId = resolvers.potionEffect("HEALTH_BOOST");
        if (healthBoost == null || healthBoostId.isEmpty()) {
            h.fail("legacy.overhealth.drainDuring", "HEALTH_BOOST did not resolve on 1.8");
            h.fail("legacy.overhealth.drainRestored", "HEALTH_BOOST did not resolve on 1.8");
            h.fail("legacy.overhealth.lockDenied", "HEALTH_BOOST did not resolve on 1.8");
            h.fail("legacy.overhealth.lockReallowed", "HEALTH_BOOST did not resolve on 1.8");
            return;
        }

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        // No setChunkForceLoaded (1.13+): the spawn chunk is kept loaded anyway (as combatCheck relies on).

        // (1) drain takes HEALTH_BOOST overhealth to the base for the window (the NMS b()/c() counter-modifier), then restores it.
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_ovhd");
            } catch (Throwable t) {
                h.fail("legacy.overhealth.drainDuring", "spawn on 1.8: " + t);
                h.fail("legacy.overhealth.drainRestored", "spawn on 1.8: " + t);
                return;
            }
            engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                    engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), () -> 0L);
            engine.sink.LegacyDispatchSink sink = new engine.sink.LegacyDispatchSink(resolvers, env);
            Scheduling.onEntity(p, () -> {
                double base = nmsMaxHealth(p);
                p.addPotionEffect(new PotionEffect(healthBoost, 100_000, 4)); // +20 max via the NMS HEALTH_BOOST modifier
                Scheduling.onEntityLater(p, 6L, () -> {
                    double boosted = nmsMaxHealth(p);
                    if (boosted <= base + 1.0e-6) {
                        h.fail("legacy.overhealth.drainDuring", "HEALTH_BOOST did not raise 1.8 max above base " + base);
                        h.fail("legacy.overhealth.drainRestored", "HEALTH_BOOST did not raise 1.8 max above base " + base);
                        FakePlayers.despawn(p);
                        return;
                    }
                    sink.drainMaxHealth(p, 1.0, base, 0.0, 40);
                    sink.flush();
                    Scheduling.onEntityLater(p, 8L, () -> h.guard("legacy.overhealth.drainDuring", () -> {
                        double now = nmsMaxHealth(p);
                        if (Math.abs(now - base) > 1.0e-3) {
                            throw new IllegalStateException("1.8 drain did not remove HEALTH_BOOST overhealth to base "
                                    + base + "; nms=" + now + " (boosted was " + boosted + ")");
                        }
                    }));
                    Scheduling.onEntityLater(p, 60L, () -> {
                        h.guard("legacy.overhealth.drainRestored", () -> {
                            double now = nmsMaxHealth(p);
                            if (Math.abs(now - boosted) > 1.0e-3) {
                                throw new IllegalStateException("1.8 drain window did not restore the boosted max "
                                        + boosted + "; nms=" + now);
                            }
                        });
                        FakePlayers.despawn(p);
                    });
                });
            });
        });

        // (2) potion-lock re-strip denies a re-application during the window; a fresh one sticks after (the 1.8 backstop).
        int lockedId = healthBoostId.getAsInt();
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_ovhl");
            } catch (Throwable t) {
                h.fail("legacy.overhealth.lockDenied", "spawn on 1.8: " + t);
                h.fail("legacy.overhealth.lockReallowed", "spawn on 1.8: " + t);
                return;
            }
            engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                    engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), () -> 0L);
            engine.sink.LegacyDispatchSink sink = new engine.sink.LegacyDispatchSink(resolvers, env);
            Scheduling.onEntity(p, () -> {
                sink.potionLock(p, lockedId, 40);
                sink.flush();
                Scheduling.onEntityLater(p, 6L, () -> { // inside the lock window
                    p.addPotionEffect(new PotionEffect(healthBoost, 200, 0));
                    Scheduling.onEntityLater(p, 4L, () -> { // let the per-tick re-strip act
                        h.guard("legacy.overhealth.lockDenied", () -> {
                            if (p.hasPotionEffect(healthBoost)) {
                                throw new IllegalStateException(
                                        "1.8 potion-lock re-strip did not deny the re-application during the window");
                            }
                        });
                        Scheduling.onEntityLater(p, 40L, () -> { // past the window: the re-strip has self-cancelled
                            p.addPotionEffect(new PotionEffect(healthBoost, 200, 0));
                            Scheduling.onEntityLater(p, 4L, () -> {
                                h.guard("legacy.overhealth.lockReallowed", () -> {
                                    if (!p.hasPotionEffect(healthBoost)) {
                                        throw new IllegalStateException(
                                                "HEALTH_BOOST did not stick after the 1.8 lock window elapsed");
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

    // ── worn max-health bonus (1.8.1): the reconciled attribute modifier must move the REAL 1.8 max health ──

    private void maxHealthCheck(Harness h) {
        h.expect("legacy.health.wornBonus");

        RegistryResolvers resolvers = new RegistryResolvers();
        Library library;
        try {
            Path root = Files.createTempDirectory("se-legacy-maxhealth");
            write(root, "crystals/naturetest.yml", """
                display: "&2NatureTest"
                description: [ "&2* test" ]
                applies-to: [ARMOR]
                abilities:
                  - { trigger: PASSIVE, effects: [ { HEALTH: { amount: 4 } } ] }
                """);
            library = LibraryLoader.load(root, ContentCompiler.production(resolvers), 0);
            if (library.hasErrors()) {
                h.fail("legacy.health.wornBonus", "crystal failed to compile on 1.8: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail("legacy.health.wornBonus", e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(new item.worn.LegacyEquipSource(), itemViews, triggers.count(),
                        triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        feature.trigger.TriggerDispatch dispatch = new feature.trigger.TriggerDispatch(executor,
                dsEnv -> new engine.sink.LegacyDispatchSink(resolvers, dsEnv), new engine.run.LegacyActorProbe(),
                holder, worn, triggers, actor -> java.util.Optional.empty(), env,
                new feature.compat.LegacyHands(), new feature.compat.LegacyDropControl());
        feature.trigger.MaxHealthDriver maxHealth = new feature.trigger.MaxHealthDriver(dispatch, holder, worn,
                env.stores().suppression(), tick::incrementAndGet,
                triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));

        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        codec.write(helmet, new CombatState(Map.of(), List.of("crystals/naturetest")));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_hp");
            } catch (Throwable t) {
                h.fail("legacy.health.wornBonus", "spawn on 1.8: " + t);
                return;
            }
            Scheduling.onEntity(p, () -> {
                double base = nmsMaxHealth(p);
                p.getInventory().setHelmet(helmet);
                worn.refresh(p, library.snapshot());
                maxHealth.refresh(p);
                Scheduling.onEntityLater(p, 10L, () -> {
                    h.guard("legacy.health.wornBonus", () -> {
                        double nms = nmsMaxHealth(p);
                        double bukkit = p.getMaxHealth();
                        // Both surfaces must move: NMS is the game truth; Bukkit is what plugins/UI read.
                        if (Math.abs(nms - (base + 4.0)) > 1e-6 || Math.abs(bukkit - (base + 4.0)) > 1e-6) {
                            throw new IllegalStateException("expected max " + (base + 4.0)
                                    + ", got nms=" + nms + " bukkit=" + bukkit);
                        }
                    });
                    FakePlayers.despawn(p);
                });
            });
        });
    }

    /**
     * The game-truth max health on 1.8: the NMS attribute VALUE (base + modifiers), read reflectively —
     * this file lives in the shared tester tree, which compiles NMS-free (the {@code Legacy*} overlay leaves
     * own the direct {@code v1_8_R3} imports).
     */
    private static double nmsMaxHealth(Player p) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Class<?> generics = Class.forName("net.minecraft.server.v1_8_R3.GenericAttributes");
            Object maxHealth = generics.getField("maxHealth").get(null);
            Class<?> iAttribute = Class.forName("net.minecraft.server.v1_8_R3.IAttribute");
            Object instance = handle.getClass().getMethod("getAttributeInstance", iAttribute)
                    .invoke(handle, maxHealth);
            return (double) instance.getClass().getMethod("getValue").invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NMS max-health read failed on 1.8", e);
        }
    }

    // ── §6 degrades (Item 3): the heroic-durability poll restores; the NMS knockback-resistance hook reduces ──

    @SuppressWarnings("deprecation") // setDurability/getDurability(short): the 1.8-correct durability API (no ItemMeta Damageable on the legacy tree).
    private void degradeChecks(Harness h) {
        h.expect("legacy.degrade.heroicSave");
        h.expect("legacy.degrade.knockbackControl");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        // Heroic durability save: a per-item heroic chance restores lost durability via the legacy gear poll. The
        // poll (started by its ctor) must first record the item's prior durability, then detect the simulated
        // loss and restore it via the heroic-save subscriber (ADR-0044 §6).
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        feature.trigger.LegacyGearPoll gearPoll = new feature.trigger.LegacyGearPoll(codec::readBlob);
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_her");
            } catch (Throwable t) {
                h.fail("legacy.degrade.heroicSave", "spawn on 1.8: " + t);
                return;
            }
            // Scope the save to THIS player: every poll in this suite scans every online player, and a save is the
            // one subscriber that mutates. Unscoped, this poll silently restores another check's wear before that
            // check's own poll can observe it (the F11 gearpoll read, which is a rise on a heroic sword too).
            feature.heroic.LegacyHeroicSave save = new feature.heroic.LegacyHeroicSave(codec, new Random());
            gearPoll.heroicSave((pl, item, priorDamage) ->
                    pl.getUniqueId().equals(p.getUniqueId()) && save.trySave(pl, item, priorDamage));
            Scheduling.onEntity(p, () -> {
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                // Always-save heroic durability (chance 1.0): nextDouble() ∈ [0,1) is always < 1.0.
                codec.write(sword, new CombatState(Map.of(), List.of(), null, null, false,
                        new HeroicStat(0.0, 0.0, 1.0), 0, null, null));
                setHand(p, sword); // durability 0 — the poll records this as the prior over the next few ticks
                Scheduling.onEntityLater(p, 4L, () -> {
                    ItemStack held = handItem(p);
                    held.setDurability((short) 50); // simulate a durability hit
                    setHand(p, held);
                    Scheduling.onEntityLater(p, 5L, () -> {
                        h.guard("legacy.degrade.heroicSave", () -> {
                            short dur = handItem(p).getDurability();
                            if (dur != 0) {
                                throw new IllegalStateException(
                                        "heroic durability did not restore the item on 1.8 (durability=" + dur + ")");
                            }
                        });
                        FakePlayers.despawn(p);
                    });
                });
            });
        });

        // KNOCKBACK_CONTROL via the NMS knockback-resistance hook: a cancel flag (multiplier 0) zeroes a hit's
        // knockback. Compared against an unflagged baseline so the assertion proves the hook acted, not that the
        // hit simply produced no knockback. Victims are offset from the attacker so the knockback has a direction.
        KnockbackControlStore store = new KnockbackControlStore();
        // 1.8 fires no Paper knockback event, so register the NMS knockback-resistance applier directly (the
        // shared KnockbackListener probe would resolve NONE here) — mirroring the legacy bindings.
        plugin.getServer().getPluginManager().registerEvents(
                new feature.combat.NmsKnockbackApplier(store, () -> 0L), plugin);
        Scheduling.onRegion(at, () -> {
            Player atk;
            Player base;
            Player ctrl;
            try {
                atk = FakePlayers.spawn(world, "se_lc_kbA");
                base = FakePlayers.spawn(world, "se_lc_kbB");
                ctrl = FakePlayers.spawn(world, "se_lc_kbC");
            } catch (Throwable t) {
                h.fail("legacy.degrade.knockbackControl", "spawn on 1.8: " + t);
                return;
            }
            base.teleport(at.clone().add(2.0, 0.0, 0.0));
            ctrl.teleport(at.clone().add(-2.0, 0.0, 0.0));
            store.control(ctrl.getUniqueId(), 0.0, 0L, 1000); // cancel knockback on the controlled victim
            Scheduling.onEntityLater(atk, 2L, () -> { // let the teleports settle so the hit has a direction
                base.damage(4.0, atk);
                double baseKb = horizontal(base.getVelocity());
                ctrl.damage(4.0, atk);
                double ctrlKb = horizontal(ctrl.getVelocity());
                h.guard("legacy.degrade.knockbackControl", () -> {
                    if (baseKb <= 1.0e-4) {
                        throw new IllegalStateException("baseline hit produced no knockback on 1.8 (can't prove "
                                + "control); base=" + baseKb);
                    }
                    if (ctrlKb >= baseKb * 0.5) {
                        throw new IllegalStateException("KNOCKBACK_CONTROL cancel did not reduce knockback on 1.8: "
                                + "base=" + baseKb + " ctrl=" + ctrlKb);
                    }
                });
                FakePlayers.despawn(atk);
                FakePlayers.despawn(base);
                FakePlayers.despawn(ctrl);
            });
        });
    }

    private static double horizontal(Vector v) {
        return Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }

    // ── PROJECTILE_LAND's 1.8 stuck-probe: the answer the hit event cannot give, read a tick later ──

    /**
     * 1.8's {@code ProjectileHitEvent} fires before the arrow branches on what it hit and before it moves to the
     * impact, so {@code LegacyProjectiles} defers to the arrow's own {@code inGround} flag one tick on. Only a
     * booted 1.8 server can say whether that flag is set by then — the whole trigger is dead on this lane if it
     * is not, silently, exactly as it was before the probe existed.
     *
     * <p>The negative half pins the documented residual: a thrown projectile is already {@code dead} when its hit
     * event fires, so the leaf declines rather than probing a corpse.
     */
    private void projectileLandChecks(Harness h) {
        h.expect("legacy.projectileland.arrowLanding");
        h.expect("legacy.projectileland.throwableDeclined");

        feature.compat.LegacyProjectiles projectiles = new feature.compat.LegacyProjectiles();
        AtomicInteger arrowLandings = new AtomicInteger();
        AtomicInteger throwableLandings = new AtomicInteger();
        AtomicReference<Location> lastLanding = new AtomicReference<>();
        Listener probe = new Listener() {
            @EventHandler
            public void onHit(ProjectileHitEvent event) {
                boolean arrow = event.getEntity() instanceof Arrow;
                projectiles.landing(event, at -> {
                    (arrow ? arrowLandings : throwableLandings).incrementAndGet();
                    lastLanding.set(at);
                });
            }
        };
        plugin.getServer().getPluginManager().registerEvents(probe, plugin);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        Scheduling.onRegion(at, () -> {
            Player shooter;
            try {
                shooter = FakePlayers.spawn(world, "se_lc_pl");
            } catch (Throwable t) {
                h.fail("legacy.projectileland.arrowLanding", "spawn on 1.8: " + t);
                HandlerList.unregisterAll(probe);
                return;
            }
            Scheduling.onEntity(shooter, () -> {
                // Dropped from above rather than launched from the hand: a point-blank launch can clip the
                // shooter, and the contract under test is the landing, not the trajectory. Offset clear of the
                // spawn point because every fake player in this suite logs in AT spawn and the server scatters
                // them a few blocks — an arrow dropped into that crowd hits a body, and an entity hit is exactly
                // what this seam declines to report.
                Location drop = at.clone().add(12.5, 4.0, 12.5);
                Arrow arrow = world.spawnArrow(drop, new Vector(0.0, -1.0, 0.0), 2.0f, 0.0f);
                arrow.setShooter(shooter);
                Snowball snowball = (Snowball) world.spawnEntity(drop, EntityType.SNOWBALL);
                snowball.setShooter(shooter);
                snowball.setVelocity(new Vector(0.0, -1.0, 0.0));
                Scheduling.onEntityLater(shooter, 60L, () -> {
                    h.guard("legacy.projectileland.arrowLanding", () -> {
                        if (arrowLandings.get() != 1) {
                            throw new IllegalStateException("expected exactly 1 arrow landing from the 1.8 "
                                    + "in-ground probe, got " + arrowLandings.get());
                        }
                        Location landed = lastLanding.get();
                        if (landed == null || landed.distance(drop) > 6.0) {
                            throw new IllegalStateException("landing anchored away from the drop column: " + landed);
                        }
                    });
                    h.guard("legacy.projectileland.throwableDeclined", () -> {
                        if (throwableLandings.get() != 0) {
                            throw new IllegalStateException("a thrown projectile reached PROJECTILE_LAND on 1.8 ("
                                    + throwableLandings.get() + ") — the leaf must decline what it cannot probe");
                        }
                    });
                    HandlerList.unregisterAll(probe);
                    FakePlayers.despawn(shooter);
                });
            });
        });
    }

    // ── §6 gear-poll identity (F10/F11): the 1.8 poll tracks each slot's combat-state blob, not just material ──

    @SuppressWarnings("deprecation") // setDurability/getDurability(short) + setItemInHand: the 1.8 durability/hand API.
    private void gearPollChecks(Harness h) {
        h.expect("legacy.gearpoll.identityRefresh");
        h.expect("legacy.gearpoll.swapNoFreeRepair");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        // F10: a same-material armour swap and an in-place blob rewrite BOTH change the poll's armour signature now,
        // so worn state refreshes within a tick; an identical re-set does NOT (no refresh churn).
        CombatCodec codecA = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        feature.trigger.LegacyGearPoll pollA = new feature.trigger.LegacyGearPoll(codecA::readBlob);
        AtomicInteger refreshes = new AtomicInteger();
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_gpr");
            } catch (Throwable t) {
                h.fail("legacy.gearpoll.identityRefresh", "spawn on 1.8: " + t);
                return;
            }
            // Filter by UUID: the shared poll scans every online player, so only this player's refresh counts here.
            pollA.refreshEquip(pl -> {
                if (pl.getUniqueId().equals(p.getUniqueId())) {
                    refreshes.incrementAndGet();
                }
            });
            Scheduling.onEntity(p, () -> {
                ItemStack enchanted = new ItemStack(Material.DIAMOND_CHESTPLATE);
                codecA.write(enchanted, new CombatState(Map.of("enchants/keen", 1), List.of()));
                p.getInventory().setChestplate(enchanted);
                Scheduling.onEntityLater(p, 5L, () -> { // baseline the enchanted piece
                    int afterBaseline = refreshes.get();
                    // (a) same Material + count, but a PLAIN piece (blob null) → signature changes → refresh.
                    p.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                    Scheduling.onEntityLater(p, 4L, () -> {
                        int afterSwap = refreshes.get();
                        // (b) in-place blob rewrite on the worn piece (the crystal-extract gesture) → refresh.
                        ItemStack worn = p.getInventory().getChestplate();
                        codecA.write(worn, new CombatState(Map.of("enchants/keen", 2), List.of()));
                        p.getInventory().setChestplate(worn);
                        Scheduling.onEntityLater(p, 4L, () -> {
                            int afterRewrite = refreshes.get();
                            // (c) negative control: re-set the identical piece (equal blob) → NO refresh.
                            ItemStack same = p.getInventory().getChestplate();
                            p.getInventory().setChestplate(same == null ? null : same.clone());
                            Scheduling.onEntityLater(p, 4L, () -> {
                                int afterResame = refreshes.get();
                                h.guard("legacy.gearpoll.identityRefresh", () -> {
                                    if (afterSwap <= afterBaseline) {
                                        throw new IllegalStateException("same-material swap did not refresh worn state "
                                                + "on 1.8 (F10): baseline=" + afterBaseline + " swap=" + afterSwap);
                                    }
                                    if (afterRewrite <= afterSwap) {
                                        throw new IllegalStateException("in-place blob rewrite did not refresh worn "
                                                + "state on 1.8 (F10): swap=" + afterSwap + " rewrite=" + afterRewrite);
                                    }
                                    if (afterResame != afterRewrite) {
                                        throw new IllegalStateException("identical re-set spuriously refreshed on 1.8 "
                                                + "(signature churn): rewrite=" + afterRewrite + " resame=" + afterResame);
                                    }
                                });
                                FakePlayers.despawn(p);
                            });
                        });
                    });
                });
            });
        });

        // F11: swapping a MORE-damaged same-material heroic item over a fresher baseline reads as a swap (identity
        // differs), so no free heroic repair and no spurious ITEM_DAMAGE; a genuine in-place wear still restores.
        CombatCodec codecB = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        feature.trigger.LegacyGearPoll pollB = new feature.trigger.LegacyGearPoll(codecB::readBlob);
        feature.heroic.LegacyHeroicSave saveB = new feature.heroic.LegacyHeroicSave(codecB, new Random());
        AtomicInteger itemDamage = new AtomicInteger();
        Scheduling.onRegion(at, () -> {
            Player p;
            try {
                p = FakePlayers.spawn(world, "se_lc_gps");
            } catch (Throwable t) {
                h.fail("legacy.gearpoll.swapNoFreeRepair", "spawn on 1.8: " + t);
                return;
            }
            // Scoped like the other two subscribers, and for the same reason — see the degrade check above.
            pollB.heroicSave((pl, item, priorDamage) ->
                    pl.getUniqueId().equals(p.getUniqueId()) && saveB.trySave(pl, item, priorDamage));
            pollB.fireItemDamage((pl, armor, delta, percent) -> {
                if (pl.getUniqueId().equals(p.getUniqueId())) {
                    itemDamage.incrementAndGet();
                }
            });
            Scheduling.onEntity(p, () -> {
                setHand(p, new ItemStack(Material.DIAMOND_SWORD)); // pristine plain sword, durability 0
                Scheduling.onEntityLater(p, 5L, () -> { // baseline the plain sword
                    int damageBeforeSwap = itemDamage.get();
                    ItemStack heroic = new ItemStack(Material.DIAMOND_SWORD);
                    // Always-save heroic durability (chance 1.0), pre-damaged to 500.
                    codecB.write(heroic, new CombatState(Map.of(), List.of(), null, null, false,
                            new HeroicStat(0.0, 0.0, 1.0), 0, null, null));
                    heroic.setDurability((short) 500);
                    setHand(p, heroic);
                    Scheduling.onEntityLater(p, 6L, () -> {
                        h.guard("legacy.gearpoll.swapNoFreeRepair", () -> {
                            short dur = handItem(p).getDurability();
                            if (dur != 500) {
                                throw new IllegalStateException("same-material swap free-repaired the heroic item on "
                                        + "1.8 (F11): durability=" + dur + " (expected 500, no restore to the plain "
                                        + "sword's 0)");
                            }
                            if (itemDamage.get() != damageBeforeSwap) {
                                throw new IllegalStateException("swap spuriously fired ITEM_DAMAGE on 1.8 (F11): "
                                        + "before=" + damageBeforeSwap + " after=" + itemDamage.get());
                            }
                        });
                        // Genuine in-place wear on the SAME heroic sword → identity matches → the save fires normally.
                        ItemStack held = handItem(p);
                        held.setDurability((short) 510);
                        setHand(p, held);
                        Scheduling.onEntityLater(p, 6L, () -> {
                            h.guard("legacy.gearpoll.swapNoFreeRepair", () -> {
                                short dur = handItem(p).getDurability();
                                if (dur != 500) {
                                    throw new IllegalStateException("identity gate broke the legitimate heroic save "
                                            + "on 1.8 (F11): durability=" + dur + " (expected 500)");
                                }
                                if (itemDamage.get() <= damageBeforeSwap) {
                                    throw new IllegalStateException("genuine in-place wear did not fire ITEM_DAMAGE on "
                                            + "1.8 (F11): before=" + damageBeforeSwap + " after=" + itemDamage.get());
                                }
                            });
                            FakePlayers.despawn(p);
                        });
                    });
                });
            });
        });
    }

    // ── Item path (no player): compile → apply → render → blob survives setItemMeta → 1.8 material degrade ──

    @SuppressWarnings("deprecation") // setDisplayName(String): the floor-stable, 1.8-present item-meta path
    private void itemChecks(Harness h) {
        h.expect("legacy.item.applyAndRender");
        h.expect("legacy.item.blobSurvivesSetItemMeta");
        h.expect("legacy.item.degradesNetherite");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        ItemEnchanter enchanter;
        try {
            Path root = Files.createTempDirectory("se-legacy-item");
            write(root, "enchants/keen.yml", KEEN);
            write(root, "sets/vanguard.yml", VANGUARD);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("legacy.item.applyAndRender", "content failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)), new item.codec.NbtItemStateStore());
            enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard(),
                    () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                    () -> ItemEnchanter.DEFAULT_MAX_MERGE, () -> java.util.List.of("SWORD", "AXE"), platform.lang.Messages.defaults(), item.mint.VanillaEnchants.NONE);
        } catch (IOException e) {
            h.fail("legacy.item.applyAndRender", e);
            return;
        }

        h.guard("legacy.item.applyAndRender", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/keen", 2).ok()) {
                throw new IllegalStateException("apply of a valid enchant was rejected on 1.8");
            }
            Integer level = codec.read(sword).enchants().get("enchants/keen");
            if (level == null || level != 2) {
                throw new IllegalStateException("blob did not record the enchant at level 2 on 1.8: " + level);
            }
            if (!renderedLoreMentions(sword, "Keen")) {
                throw new IllegalStateException("lore was not rendered with the enchant name on 1.8");
            }
        });

        // THE classic 1.8 trap: there is no PDC, so the blob lives in the item's NMS NBT, and a later
        // setItemMeta() (e.g. a rename scroll) reconstructs the tag and can WIPE unknown data. The legacy
        // ItemBlobStore parks it in CraftMetaItem's unhandled-tags map, which setItemMeta serialises back —
        // this check fails loudly if that ever regresses (a modern PDC server can never exercise it).
        h.guard("legacy.item.blobSurvivesSetItemMeta", () -> {
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            if (!enchanter.applyEnchant(sword, "enchants/keen", 1).ok()) {
                throw new IllegalStateException("setup: enchant did not apply");
            }
            String blobBefore = codec.readBlob(sword);
            if (blobBefore == null || blobBefore.isEmpty()) {
                throw new IllegalStateException("no blob was written by apply on 1.8");
            }
            ItemMeta meta = sword.getItemMeta();
            if (meta == null) {
                throw new IllegalStateException("diamond sword had no ItemMeta");
            }
            meta.setDisplayName("renamed after enchant"); // a mutation through the meta API, as a rename does
            sword.setItemMeta(meta);
            String blobAfter = codec.readBlob(sword);
            // Byte-identity, not just key-presence: catches partial NBT corruption, not only a full wipe.
            if (!blobBefore.equals(blobAfter)) {
                throw new IllegalStateException("blob changed across setItemMeta on 1.8 (NBT-survival regression): '"
                        + blobBefore + "' -> '" + blobAfter + "'");
            }
            if (!codec.read(sword).enchants().containsKey("enchants/keen")) {
                throw new IllegalStateException("enchant lost after setItemMeta on 1.8 (NBT-wipe trap)");
            }
        });

        // The item-2 degradation: NETHERITE_* armour has no 1.8 material, so a set mint must fall to the
        // DIAMOND equivalent, NOT the generic LEATHER_HELMET caller fallback.
        h.guard("legacy.item.degradesNetherite", () -> {
            Material degraded = ItemFactory.material("NETHERITE_HELMET", Material.LEATHER_HELMET);
            if (degraded == Material.LEATHER_HELMET) {
                throw new IllegalStateException("NETHERITE_HELMET fell to the generic LEATHER fallback, not DIAMOND");
            }
            if (degraded != Material.DIAMOND_HELMET) {
                throw new IllegalStateException("NETHERITE_HELMET did not degrade to DIAMOND_HELMET on 1.8: " + degraded);
            }
            var minted = enchanter.mintSetPiece("sets/vanguard", "helmet");
            if (minted.isEmpty() || !minted.get().getType().name().endsWith("HELMET")) {
                throw new IllegalStateException("the Vanguard helmet member did not mint to a helmet on 1.8");
            }
        });
    }

    // ── Combat spine (fake player): a Venom sword poisons a cow + fires EnchantActivateEvent (ADR-0014) ──

    private void combatCheck(Harness h) {
        h.expect("legacy.combat.enchantFiresOnHit");
        h.expect("legacy.combat.eventFired");

        // Legacy seam: the sink resolves interned handles through the SAME RegistryResolvers used to compile, so
        // the compile→runtime potion id pairs. (Modern wraps this in RuntimeHandles, which the 1.8 overlay omits.)
        RegistryResolvers resolvers = new RegistryResolvers();
        Library library;
        PotionEffectType poison = poisonType();
        try {
            Path root = Files.createTempDirectory("se-legacy-combat");
            write(root, "enchants/venom.yml", VENOM);
            library = LibraryLoader.load(root, ContentCompiler.production(resolvers), 0);
            if (library.hasErrors()) {
                h.fail("legacy.combat.enchantFiresOnHit", "venom failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            if (poison == null) {
                h.fail("legacy.combat.enchantFiresOnHit", "POISON did not resolve on 1.8");
                return;
            }
        } catch (IOException e) {
            h.fail("legacy.combat.enchantFiresOnHit", e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(new item.worn.LegacyEquipSource(), itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        EventProbe probe = new EventProbe();
        plugin.getServer().getPluginManager().registerEvents(probe, plugin);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE,
                (key, ability, ctx) -> {
                    Player actor = ctx.actor();
                    if (actor == null || key == null) {
                        return;
                    }
                    plugin.getServer().getPluginManager().callEvent(
                            new EnchantActivateEvent(actor, key, ability.level()));
                });
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        CombatDispatch dispatch = new CombatDispatch(executor, dsEnv -> new engine.sink.LegacyDispatchSink(resolvers, dsEnv), new engine.run.LegacyActorProbe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> java.util.Optional.empty(), env, CombatDispatch.Caps.unlimited(), new feature.compat.LegacyProjectiles());
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/venom", 1), List.of()));
        int attackId = triggers.idOf("ATTACK").orElseThrow();

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        // No setChunkForceLoaded here: it is 1.13+ (absent on 1.8) and the spawn chunk is kept loaded anyway.
        Scheduling.onRegion(at, () -> {
            Player attacker;
            LivingEntity victim;
            try {
                attacker = FakePlayers.spawn(world, "se_lc_atk");
                victim = (LivingEntity) world.spawnEntity(at, EntityType.COW);
            } catch (Throwable t) {
                h.fail("legacy.combat.enchantFiresOnHit", "victim/attacker spawn on 1.8: " + t);
                HandlerList.unregisterAll(probe);
                return;
            }
            Scheduling.onEntity(attacker, () -> {
                setHand(attacker, sword); // 1.8: setItemInHand, never setItemInMainHand
                worn.refresh(attacker, library.snapshot());
                WornState wornState = worn.get(attacker.getUniqueId());
                int candidates = wornState == null ? -1 : wornState.byTrigger(attackId).length;
                victim.damage(1.0, attacker);
                Scheduling.onEntityLater(victim, 10L, () -> {
                    h.guard("legacy.combat.enchantFiresOnHit", () -> {
                        if (candidates <= 0) {
                            throw new IllegalStateException("no Venom candidate resolved (candidates=" + candidates
                                    + ") — worn resolve/equip issue on 1.8");
                        }
                        if (!victim.hasPotionEffect(poison)) {
                            throw new IllegalStateException("candidates=" + candidates
                                    + " but cow not poisoned — EDBE/dispatch issue on 1.8");
                        }
                    });
                    h.guard("legacy.combat.eventFired", () -> {
                        if (probe.count() != 1) {
                            throw new IllegalStateException("expected exactly 1 EnchantActivateEvent, got " + probe.count());
                        }
                        if (!"enchants/venom".equals(probe.lastKey())) {
                            throw new IllegalStateException("event carried wrong key: " + probe.lastKey());
                        }
                    });
                    HandlerList.unregisterAll(probe);
                    victim.remove();
                    FakePlayers.despawn(attacker);
                });
            });
        });
    }

    // ── GUI (fake player): the enchant menu opens, the click routes, and the held item gains the enchant ──

    private void guiCheck(Harness h) {
        h.expect("legacy.gui.menuApplies");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), new item.codec.NbtItemStateStore());
        EnchantMenu menu;
        try {
            Path root = Files.createTempDirectory("se-legacy-gui");
            write(root, "enchants/keen.yml", KEEN);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                h.fail("legacy.gui.menuApplies", "content failed to compile on 1.8: " + library.diagnostics());
                return;
            }
            ContentHolder holder = new ContentHolder(library);
            LoreRenderer lore = new LoreRenderer(LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> holder.library().displayNameOf(key)), new item.codec.NbtItemStateStore());
            ItemEnchanter enchanter = new ItemEnchanter(codec, lore, holder, ItemGroups.standard(),
                    () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                    () -> ItemEnchanter.DEFAULT_MAX_MERGE, () -> java.util.List.of("SWORD", "AXE"), platform.lang.Messages.defaults(), item.mint.VanillaEnchants.NONE);
            // caps drives the cross-version title cap (1.8 rejects titles > 32 chars) — already version-aware.
            menu = new EnchantMenu(holder, enchanter, player -> { }, Capabilities.probe(plugin.getServer()), new feature.compat.LegacyHands());
        } catch (IOException e) {
            h.fail("legacy.gui.menuApplies", e);
            return;
        }

        MenuListener listener = new MenuListener(new feature.compat.LegacyHands());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        Scheduling.onRegion(at, () -> {
            Player player;
            try {
                player = FakePlayers.spawn(world, "se_lc_gui");
            } catch (Throwable t) {
                h.fail("legacy.gui.menuApplies", "spawn on 1.8: " + t);
                HandlerList.unregisterAll(listener);
                return;
            }
            Scheduling.onEntity(player, () -> {
                setHand(player, new ItemStack(Material.DIAMOND_SWORD));
                MenuHolder menuHolder = new MenuHolder(menu);
                menu.render(menuHolder);
                InventoryView view = player.openInventory(menuHolder.getInventory());
                // The first paged content icon, read from production geometry (ADR-0030 bordered the menu, so
                // content now starts at slot 10, not 0) — single-sourced, not a literal.
                int firstContent = feature.menu.MenuLayout.paged("x").contentSlot(0);
                InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                        firstContent, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                plugin.getServer().getPluginManager().callEvent(click);
                h.guard("legacy.gui.menuApplies", () -> {
                    if (!click.isCancelled()) {
                        throw new IllegalStateException("menu click was not cancelled on 1.8 — items could be moved");
                    }
                    CombatState state = codec.read(handItem(player));
                    if (!state.enchants().containsKey("enchants/keen")) {
                        throw new IllegalStateException("held item did not gain enchants/keen after the click on 1.8; "
                                + "enchants=" + state.enchants());
                    }
                });
                player.closeInventory();
                HandlerList.unregisterAll(listener);
                FakePlayers.despawn(player);
            });
        });
    }

    // ── 1.8-safe helpers (the floor's deprecated-not-removed API is the only one 1.8 has) ──

    @SuppressWarnings("deprecation")
    private static PotionEffectType poisonType() {
        return PotionEffectType.getByName("POISON");
    }

    @SuppressWarnings("deprecation")
    private static void setHand(Player player, ItemStack stack) {
        player.getInventory().setItemInHand(stack);
    }

    @SuppressWarnings("deprecation")
    private static ItemStack handItem(Player player) {
        return player.getInventory().getItemInHand();
    }

    @SuppressWarnings("deprecation") // getLore(): deprecated-not-removed across the whole range.
    private static boolean renderedLoreMentions(ItemStack stack, String fragment) {
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        return lore != null && lore.stream().anyMatch(line -> line.contains(fragment));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    /** Records {@link EnchantActivateEvent} deliveries; volatile/atomic since the proc fires on the hit thread. */
    private static final class EventProbe implements org.bukkit.event.Listener {
        private final AtomicInteger count = new AtomicInteger();
        private volatile String lastKey;

        @org.bukkit.event.EventHandler
        public void onActivate(EnchantActivateEvent event) {
            lastKey = event.getEnchantKey();
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }

        String lastKey() {
            return lastKey;
        }
    }
}
