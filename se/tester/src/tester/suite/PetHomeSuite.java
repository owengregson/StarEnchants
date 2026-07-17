package tester.suite;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.PetFoodConfig;
import compile.load.PetItemConfig;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.compat.Sounds;
import feature.fx.ParticleFx;
import feature.pet.PetArmedStore;
import feature.pet.PetHomeStore;
import feature.pet.PetLevelCue;
import feature.pet.PetMessenger;
import feature.pet.PetService;
import feature.pet.PetWornSource;
import feature.trigger.TriggerDispatch;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.PetCodec;
import item.head.HeadEquip;
import item.head.TexturedHeads;
import item.mint.VanillaEnchants;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.lang.Messages;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The ADR-0061 Mole dig/recall flow, live — the seams only a booted server proves: (a) sneak + right-click
 * DIGS (the %sneaking% condition passes through the real fact populator and the home window arms), (b) a
 * plain click within range RECALLS — the real Sink.teleport intent hops the player's own entity scheduler
 * back to the dug spot and the window is consumed, (c) beyond range the recall is refused and the window
 * SURVIVES for a retry, (d) an armed TeleblockStore flag denies the recall (window kept) and lifting it lets
 * the same window land, and (e) an unconsumed window expires on the real scheduler. Test-owned def; clientless
 * fake player (setSneaking is a server-side flag the fact populator reads).
 */
public final class PetHomeSuite implements Harness.Scenario {

    private static final String MOLE = """
            display: Mole
            type: ACTIVE
            levels:
              1: { cooldown: 0, condition: "%sneaking%", effects: [ { DIG_HOME: { window: 200, range: 8 } } ] }
            """;

    private static final String DIG = "pets.moleDigArmsTheHomeWindow";
    private static final String RECALL = "pets.moleRecallTeleportsHomeAndConsumes";
    private static final String OUT_OF_RANGE = "pets.moleOutOfRangeKeepsTheWindow";
    private static final String TELEBLOCKED = "pets.moleTeleblockDeniesRecall";
    private static final String TELEBLOCK_LIFTED = "pets.moleLiftedTeleblockRecalls";
    private static final String EXPIRES = "pets.moleWindowExpiresUnused";

    /** The recall teleport is async on modern (teleportAsync) — settle before asserting arrival. */
    private static final long SETTLE_TICKS = 10L;

    private final Plugin plugin;

    public PetHomeSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(DIG);
        h.expect(RECALL);
        h.expect(OUT_OF_RANGE);
        h.expect(TELEBLOCKED);
        h.expect(TELEBLOCK_LIFTED);
        h.expect(EXPIRES);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-pet-home-suite");
            Path file = root.resolve("pets/mole.yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, MOLE, StandardCharsets.UTF_8);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failAll(h, "mole failed to compile: " + library.diagnostics());
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
        AtomicLong clock = new AtomicLong(); // the real-tick clock a 1-tick repeater advances (see below)
        PetArmedStore armed = new PetArmedStore();
        PetHomeStore homes = new PetHomeStore();
        EngineStores stores = EngineStores.fresh();
        PetWornSource petSource = new PetWornSource(() -> true, petCodec, holder::library, armed, clock::get);
        WornStateStore worn = new WornStateStore(new WornResolver(Stores.equip(), itemViews,
                triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers(),
                () -> WornResolver.Features.ALL, Set::of, petSource)::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, stores, tick::incrementAndGet);
        TriggerDispatch dispatch = new TriggerDispatch(executor,
                dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers, actor -> Optional.empty(), env, Stores.hands(), Stores.dropControl());

        Messages messages = Messages.defaults();
        PetMessenger messenger = new PetMessenger(messages, MasterConfig.PetsSection::defaults);
        PetLevelCue cue = new PetLevelCue(MasterConfig.PetsSection::defaults, ParticleFx.NONE, Sounds.NONE);
        PetService pets = new PetService(holder, petCodec, dispatch, TexturedHeads.NONE, HeadEquip.NONE,
                VanillaEnchants.NONE, messenger, armed,
                MasterConfig.PetsSection::defaults, PetItemConfig::defaults, PetFoodConfig::defaults,
                p -> worn.refresh(p, holder.snapshot()), clock::get, m -> m == Material.AIR,
                cue, new Random(42), homes, stores.teleblock());

        CombatRig rig = new CombatRig(plugin);
        // The store expiry compares against the injected clock — advance it with the real server ticks.
        TaskHandle[] clockTask = new TaskHandle[1];
        clockTask[0] = Scheduling.repeatingGlobal(1L, 1L, clock::incrementAndGet);

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        rig.onArena(at, () -> {
            Player user;
            try {
                user = rig.spawnFake(world, "se_pethome");
            } catch (Throwable t) {
                if (clockTask[0] != null) {
                    clockTask[0].cancel();
                }
                failAll(h, "fake-player spawn: " + t);
                return;
            }
            Scheduling.onEntity(user, () -> {
                ItemStack mole = new ItemStack(Material.PAPER);
                petCodec.stamp(mole, "mole", 1);
                user.getInventory().setItem(0, mole);
                worn.refresh(user, holder.snapshot());

                // (a) DIG: sneak + use → the window arms at the dug spot.
                Location dug = user.getLocation().clone();
                user.setSneaking(true);
                pets.use(user, mole);
                user.setSneaking(false);
                h.guard(DIG, () -> {
                    if (homes.get(user.getUniqueId(), clock.get()) == null) {
                        throw new IllegalStateException("sneak+use did not arm the home window");
                    }
                });

                // (b) RECALL: walk 4 blocks (inside range 8), plain use → teleported back, window consumed.
                hop(user, dug.clone().add(4, 0, 0), () -> {
                    pets.use(user, mole);
                    Scheduling.onEntityLater(user, SETTLE_TICKS, () -> {
                        h.guard(RECALL, () -> {
                            double d = user.getLocation().distanceSquared(dug);
                            if (d > 2.25) { // within 1.5 blocks of the dug spot
                                throw new IllegalStateException("recall did not land home: d²=" + d);
                            }
                            if (homes.get(user.getUniqueId(), clock.get()) != null) {
                                throw new IllegalStateException("recall did not consume the window");
                            }
                        });
                        stageOutOfRange(h, user, mole, pets, homes, stores, clock, clockTask, rig, dug);
                    });
                });
            });
        });
    }

    private void stageOutOfRange(Harness h, Player user, ItemStack mole, PetService pets, PetHomeStore homes,
                                 EngineStores stores, AtomicLong clock, TaskHandle[] clockTask, CombatRig rig,
                                 Location dug) {
        // Re-dig (cooldown 0), then step far outside range 8: the recall must refuse AND keep the window.
        user.setSneaking(true);
        pets.use(user, mole);
        user.setSneaking(false);
        hop(user, dug.clone().add(30, 0, 0), () -> {
            Location far = user.getLocation().clone();
            pets.use(user, mole);
            Scheduling.onEntityLater(user, SETTLE_TICKS, () -> {
                h.guard(OUT_OF_RANGE, () -> {
                    if (user.getLocation().distanceSquared(far) > 1.0) {
                        throw new IllegalStateException("an out-of-range recall moved the player");
                    }
                    if (homes.get(user.getUniqueId(), clock.get()) == null) {
                        throw new IllegalStateException("an out-of-range recall consumed the window");
                    }
                });

                // (d) TELEBLOCK: back inside range, flag armed → refused, window kept; lifted → it lands.
                hop(user, dug.clone().add(4, 0, 0), () -> {
                    stores.teleblock().block(user.getUniqueId(), clock.get(), 12000);
                    pets.use(user, mole);
                    Scheduling.onEntityLater(user, SETTLE_TICKS, () -> {
                        h.guard(TELEBLOCKED, () -> {
                            if (user.getLocation().distanceSquared(dug) < 4.0) {
                                throw new IllegalStateException("a teleblocked recall still teleported");
                            }
                            if (homes.get(user.getUniqueId(), clock.get()) == null) {
                                throw new IllegalStateException("a teleblocked recall consumed the window");
                            }
                        });
                        stores.teleblock().clear(user.getUniqueId());
                        pets.use(user, mole);
                        Scheduling.onEntityLater(user, SETTLE_TICKS, () -> {
                            h.guard(TELEBLOCK_LIFTED, () -> {
                                if (user.getLocation().distanceSquared(dug) > 2.25) {
                                    throw new IllegalStateException("the lifted-teleblock recall did not land");
                                }
                                if (homes.get(user.getUniqueId(), clock.get()) != null) {
                                    throw new IllegalStateException("the landed recall did not consume");
                                }
                            });

                            // (e) EXPIRY: dig once more, never recall — the scheduled task clears it.
                            user.setSneaking(true);
                            pets.use(user, mole);
                            user.setSneaking(false);
                            Scheduling.onEntityLater(user, 200L + 30L, () -> { // window 200 + margin
                                h.guard(EXPIRES, () -> {
                                    if (homes.get(user.getUniqueId(), clock.get()) != null) {
                                        throw new IllegalStateException("the window outlived its expiry");
                                    }
                                });
                                if (clockTask[0] != null) {
                                    clockTask[0].cancel();
                                }
                                rig.teardown();
                            });
                        });
                    });
                });
            });
        });
    }

    /**
     * Folia-safe staging hop: a synchronous {@code Player.teleport} throws on Folia even from the entity's
     * own region thread ("Must use teleportAsync while in region threading"). Continue only once the hop has
     * landed — the +30 hop leaves the force-loaded arena chunk, so a fixed delay alone races the async chunk
     * load — then settle on the player's entity thread. Always continues: a refused hop surfaces as a
     * resolved guard FAIL, never an unresolved timeout.
     */
    private static void hop(Player user, Location to, Runnable then) {
        user.teleportAsync(to).whenComplete((landed, err) -> Scheduling.onEntityLater(user, SETTLE_TICKS, then));
    }

    private void failAll(Harness h, String message) {
        h.fail(DIG, message);
        h.fail(RECALL, message);
        h.fail(OUT_OF_RANGE, message);
        h.fail(TELEBLOCKED, message);
        h.fail(TELEBLOCK_LIFTED, message);
        h.fail(EXPIRES, message);
    }
}
