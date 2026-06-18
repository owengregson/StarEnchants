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
 * The TELEPORT effect, live (docs/architecture.md §7; v3.3 §C) — the first user of the Sink's
 * {@code teleport} intent / {@code teleportAsync}, so this proves that path works on Paper and Folia. A
 * fake attacker with a {@code TELEPORT:VICTIM} (ATTACK) enchant hits a cow spawned 5 blocks away; the
 * enchant teleports the attacker (@Self) to the victim, so afterwards the attacker has moved well away
 * from its spawn. The check POLLS the attacker's HORIZONTAL distance-from-spawn each tick until the
 * (async) teleport lands — distance-from-spawn rather than distance-to-cow is robust against the cow
 * drifting, horizontal-only ignores a not-yet-teleported player's gravity fall, and the per-tick poll
 * (vs a fixed one-shot wait) makes it correct under concurrent matrix load where {@code teleportAsync}
 * can resolve several ticks late. Mojang- and spigot-mapped alike (needs the fake-player attacker).
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
                    // 8 blocks away (same region) so the teleport is observable but stays in-region. The gap
                    // is deliberately wider than the bare minimum: on the 1.17.1 floor a teleport into a
                    // mob's space lands the player a couple of blocks SHORT of the target (collision push-out),
                    // so a 5-block gap left the move (~2.5) just under the threshold. 8 blocks clears it with
                    // margin on every platform while a non-teleport still reads ~0.
                    victim = (LivingEntity) world.spawnEntity(spawn.clone().add(0, 0, 8), EntityType.COW);
                    // Pin the cow: a wandering cow drifts toward the attacker before the hit, shrinking the
                    // teleport distance below the threshold (the flake that surfaced as "moved 2.12 blocks").
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
                    // teleportAsync resolves a tick or more later (always async on Folia, and later still under
                    // concurrent matrix load) — so POLL each tick until the move lands rather than asserting once
                    // at a fixed +10 (which raced the async teleport: a slow run measured only the residual fall
                    // and read "moved 2.92"). HORIZONTAL distance is used so a not-yet-teleported player's gravity
                    // fall (pure −Y) can never be mistaken for the teleport (a pure-X/Z hop toward the cow). Tick-
                    // anchored on the attacker's own scheduler, which follows it across the move.
                    awaitTeleport(attacker, origin, 3.0, 80, h, () -> {
                        victim.remove();
                        FakePlayers.despawn(attacker);
                    });
                });
            });
        });
    }

    /**
     * Poll, once per game tick on {@code actor}'s own scheduler, until it has moved at least
     * {@code minHorizontal} blocks HORIZONTALLY from {@code origin} (the teleport landed) — passing
     * {@code teleport.movesActorToVictim} as soon as it does, or failing it if {@code maxTicks} elapse
     * first (a real teleport that never resolved). Tick-anchored, not wall-clock, so it is correct under
     * concurrent matrix load; horizontal-only so a not-yet-teleported player's gravity fall is ignored.
     * Runs {@code cleanup} (remove the cow, despawn the fake player) exactly once on either outcome.
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
