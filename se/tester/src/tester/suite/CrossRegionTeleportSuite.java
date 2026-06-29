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
 * TELEPORT across a Folia region boundary (folia-scheduling; live-server-testing "Folia in the suite"). This is
 * the one path that proves {@code DispatchSink.teleport} actually hops to the actor's OWN scheduler rather than
 * teleporting inline on the firing region's thread — the difference is invisible on Paper (one thread) and a
 * hard {@code IllegalStateException} on Folia. A naive inline teleport would pass every Paper run and wedge on a
 * real Folia server; only a genuinely cross-region scenario catches it.
 *
 * <p><strong>Staging.</strong> The attacker sits at world spawn; the cow victim sits 512 blocks away — far
 * enough to own a distinct Folia region. The hit is fired as {@code victim.damage(attacker)} ON THE VICTIM's
 * region thread — matching where Folia delivers a real combat event (the victim is mutated, so its region owns
 * the event). The victim is therefore touched on its own thread; the attacker is only referenced, and the
 * resulting {@code TELEPORT:VICTIM} must carry the far attacker across the boundary to the cow via the Sink's
 * {@code onEntity(attacker)} hop. We then poll the attacker's own location on its own scheduler (which follows
 * it across the boundary) until it has arrived. The damage call is guarded: if a server rejects referencing a
 * cross-region attacker the failure is reported with the throwable rather than silently stalling. Mojang-mapped
 * only (fake-player attacker).
 */
public final class CrossRegionTeleportSuite implements Harness.Scenario {

    private static final String BLINK = """
            display: Blink
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ TELEPORT: { to: VICTIM } }] }
            """;

    /** 512 blocks (32 chunks): comfortably a different Folia region from world spawn, well inside any world. */
    private static final int REGION_GAP = 512;
    /** Arrived once within this many blocks of the victim — the cross-region hop physically landed. */
    private static final double ARRIVED = 4.0;
    /** Generous: a cross-region {@code teleportAsync} may take a few extra ticks; PASS fires on arrival anyway. */
    private static final int BUDGET_TICKS = 200;

    private final Plugin plugin;

    public CrossRegionTeleportSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("teleport.crossRegionHopLandsActorOnVictim");
        String check = "teleport.crossRegionHopLandsActorOnVictim";

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-xregion-teleport-suite");
            write(root, "enchants/blink.yml", BLINK);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                h.fail(check, "blink failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            h.fail(check, e.toString());
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        AtomicLong tick = new AtomicLong();
        CombatDispatch dispatch = new CombatDispatch(executor, new engine.sink.DispatchSinkFactory(handles), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::incrementAndGet);
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new CombatListener(dispatch));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        codec.write(sword, new CombatState(Map.of("enchants/blink", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location attackerAt = world.getSpawnLocation();
        Location victimAt = attackerAt.clone().add(REGION_GAP, 0, REGION_GAP);

        // Two-chunk arena: force-load BOTH regions, then stage the attacker on the (primary) world-spawn thread.
        rig.onArena(attackerAt, victimAt, () -> {
            Player attacker;
            try {
                attacker = rig.track(FakePlayers.spawn(world, "se_xtp_atk")); // FakePlayers spawns at world spawn
                attacker.getInventory().setItemInMainHand(sword);
                worn.refresh(attacker, library.snapshot());
            } catch (Throwable t) {
                h.fail(check, "attacker spawn: " + t);
                rig.teardown();
                return;
            }
            // Hop to the victim's distinct region to stage the cow and fire the hit there — where Folia delivers
            // a real combat event (the victim's region owns it).
            Scheduling.onRegion(victimAt, () -> {
                LivingEntity victim;
                try {
                    victim = rig.spawn(world, victimAt, EntityType.COW, LivingEntity.class);
                    victim.setAI(false); // pin the cow so the landing point we capture is the one we measure
                } catch (Throwable t) {
                    h.fail(check, "victim spawn: " + t);
                    rig.teardown();
                    return;
                }
                Location landing = victim.getLocation().clone(); // captured on the victim's thread; cow is pinned
                try {
                    // The victim is local (we own its region); the attacker is in another region and is only
                    // referenced. The TELEPORT:VICTIM proc must carry the attacker here via the Sink's entity hop
                    // — if that hop teleported inline on this thread it would throw a wrong-region access on Folia.
                    victim.damage(1.0, attacker);
                } catch (Throwable t) {
                    h.fail(check, "cross-region hit threw on the victim thread: " + t);
                    rig.teardown();
                    return;
                }
                Scheduling.onEntity(attacker,
                        () -> Proximity.awaitWithin(attacker, landing, ARRIVED, BUDGET_TICKS, h, check, rig::teardown));
            });
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
