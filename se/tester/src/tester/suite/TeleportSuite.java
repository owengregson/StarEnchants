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
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The TELEPORT effect, live (§7; v3.3 §C) — the first user of the Sink's {@code teleport} intent. A
 * {@code TELEPORT:VICTIM} ATTACK enchant teleports the attacker to its cow victim, here within ONE region.
 * Asserts the actual semantic — the attacker ends up AT the victim — by polling its proximity to the victim's
 * captured location each tick (tolerant of collision push-out and of {@code teleportAsync} landing several ticks
 * late under matrix load). The cross-region hop is proven separately in {@link CrossRegionTeleportSuite}.
 * Fake-player attacker.
 */
public final class TeleportSuite implements Harness.Scenario {

    private static final String BLINK = """
            display: Blink
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ TELEPORT: { to: VICTIM } }] }
            """;

    /** Same region as the attacker, far enough that a real teleport is unambiguous and a no-teleport reads ~8. */
    private static final int VICTIM_GAP = 8;
    /** Within this many blocks of the victim counts as "arrived": absorbs the floor's collision push-out. */
    private static final double ARRIVED = 4.0;
    /** Generous budget: PASS fires the instant the actor arrives, so a wide cap only lengthens the FAIL path. */
    private static final int BUDGET_TICKS = 160;

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
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(dispatch));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/blink", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();

        rig.onArena(spawn, () -> {
            Player attacker;
            LivingEntity victim;
            try {
                attacker = rig.track(FakePlayers.spawn(world, "se_tp_atk"));
                victim = rig.spawn(world, spawn.clone().add(0, 0, VICTIM_GAP), EntityType.COW, LivingEntity.class);
                // Pin the cow: a wanderer drifts toward the attacker pre-hit, shrinking the gap we measure.
                victim.setAI(false);
            } catch (Throwable t) {
                h.fail("teleport.movesActorToVictim", "spawn: " + t);
                rig.teardown();
                return;
            }
            Location victimAt = victim.getLocation().clone(); // captured on the victim's thread; cow is pinned
            Scheduling.onEntity(attacker, () -> {
                attacker.getInventory().setItemInMainHand(sword);
                worn.refresh(attacker, library.snapshot());
                victim.damage(1.0, attacker); // same-region programmatic hit → ATTACK → TELEPORT to the victim
                Proximity.awaitWithin(attacker, victimAt, ARRIVED, BUDGET_TICKS, h,
                        "teleport.movesActorToVictim", rig::teardown);
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
