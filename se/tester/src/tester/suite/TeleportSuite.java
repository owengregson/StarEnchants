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
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * The TELEPORT effect, live (§7; v3.3 §C) — the first user of the Sink's {@code teleport}/
 * {@code teleportAsync} intent. A {@code TELEPORT:VICTIM} ATTACK enchant teleports the attacker to its cow
 * victim. POLLS horizontal distance each tick: horizontal ignores gravity drift, the per-tick poll tolerates
 * {@code teleportAsync} landing several ticks late under matrix load. Fake-player attacker.
 */
public final class TeleportSuite implements Harness.Scenario {

    private static final String BLINK = """
            display: Blink
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["TELEPORT:VICTIM"] }
            """;

    private final Plugin plugin;

    public TeleportSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("teleport.movesActorToVictim");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-teleport-suite");
            write(root, "enchants/blink.yml", BLINK);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail("teleport.movesActorToVictim", "blink failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail("teleport.movesActorToVictim", e.toString());
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
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(dispatch), plugin);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/blink", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(spawn, () -> {
                Player attacker;
                LivingEntity victim;
                try {
                    attacker = FakePlayers.spawn(world, "se_tp_atk");
                    // 8 blocks (same region, observable). Wider than minimum: on the 1.17.1 floor a teleport
                    // into a mob's space lands a couple of blocks SHORT (collision push-out), so 8 clears the
                    // threshold with margin everywhere while a non-teleport still reads ~0.
                    victim = (LivingEntity) world.spawnEntity(spawn.clone().add(0, 0, 8), EntityType.COW);
                    // Pin the cow: a wanderer drifts toward the attacker pre-hit, shrinking the move distance.
                    victim.setAI(false);
                } catch (Throwable t) {
                    h.fail("teleport.movesActorToVictim", "spawn: " + t);
                    return;
                }
                Scheduling.onEntity(attacker, () -> {
                    Location origin = attacker.getLocation().clone();
                    attacker.getInventory().setItemInMainHand(sword);
                    worn.refresh(attacker, library.snapshot());
                    victim.damage(1.0, attacker); // programmatic hit (range-independent) → ATTACK → TELEPORT
                    awaitTeleport(attacker, origin, 3.0, 80, h, () -> {
                        victim.remove();
                        FakePlayers.despawn(attacker);
                    });
                });
            });
        });
    }

    /**
     * Poll once per tick on {@code actor}'s own scheduler until it has moved {@code minHorizontal} blocks
     * horizontally from {@code origin} (pass), or {@code maxTicks} elapse (fail). Tick-anchored, not
     * wall-clock, so correct under matrix load. Runs {@code cleanup} exactly once on either outcome.
     */
    private static void awaitTeleport(Player actor, Location origin, double minHorizontal, int maxTicks,
                                      Harness h, Runnable cleanup) {
        awaitTeleportStep(actor, origin, minHorizontal, 0, maxTicks, h, cleanup);
    }

    private static void awaitTeleportStep(Player actor, Location origin, double minHorizontal, int tick,
                                          int maxTicks, Harness h, Runnable cleanup) {
        double moved = horizontalDistance(actor.getLocation(), origin);
        if (moved >= minHorizontal) {
            h.pass("teleport.movesActorToVictim");
            cleanup.run();
            return;
        }
        if (tick >= maxTicks) {
            h.fail("teleport.movesActorToVictim", "attacker did not teleport toward the victim within "
                    + maxTicks + " ticks (moved " + String.format("%.2f", moved) + " blocks horizontally)");
            cleanup.run();
            return;
        }
        Scheduling.onEntityLater(actor, 1L,
                () -> awaitTeleportStep(actor, origin, minHorizontal, tick + 1, maxTicks, h, cleanup));
    }

    /** Distance between two locations ignoring the Y axis — immune to a falling player's vertical drift. */
    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
