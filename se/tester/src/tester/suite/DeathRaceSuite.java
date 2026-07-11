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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * ADR-0051 heal-vs-death ordering, live — a booted server's vanilla kill decision is the only oracle for it.
 * Two contracts: a zero-WAIT DEFENSE self-heal on a lethal hit joins the kill decision (the victim survives —
 * Phoenix's advertised "a blow that would kill you instead heals"), and a heal landing AFTER a real death is
 * dropped (the dead stay dead — never a half-dead ghost holding health after its death event already fired).
 * Mojang-mapped only (fake-player actors).
 */
public final class DeathRaceSuite implements Harness.Scenario {

    private static final String SAVE_KEY = "deathrace.sameHitHealJoinsTheKillDecision";
    private static final String DEAD_KEY = "deathrace.aLateHealNeverRevivesTheDead";

    private static final String SAVE = """
            display: Deathrace Save
            trigger: DEFENSE
            applies-to: [BOOTS]
            levels:
              1: { chance: 100, effects: [{ MODIFY_HEALTH: { amount: 10, mode: "give", who: "@Self" } }] }
            """;

    /** The half-death shape: the heal's WAIT tier fires only after the hit — and any death — fully resolved. */
    private static final String LATE = """
            display: Deathrace Late
            trigger: DEFENSE
            applies-to: [BOOTS]
            levels:
              1: { chance: 100, effects: [{ WAIT: 1 }, { MODIFY_HEALTH: { amount: 10, mode: "give", who: "@Self" } }] }
            """;

    /** Staged pre-hit health; the heal (+10) and any armor formula keep the survive-band bounds valid. */
    private static final double STAGED_HEALTH = 5.0;
    /** Lethal against STAGED_HEALTH through leather boots on every supported armor formula (final ≥ 7.3). */
    private static final double HIT = 8.0;
    /** Region-timer ticks to let the WAIT-1 tier fire (the corpse's own entity scheduler may retire tasks). */
    private static final long LATE_BUDGET = 4L;
    /**
     * Fake players join with vanilla's 60-tick spawn invulnerability ({@code ServerPlayer.spawnInvulnerableTime});
     * a hit inside that window silently no-ops ({@code hurt} returns false, no event). Waiting it out is
     * mapping-proof on every floor version, unlike clearing the (per-mapping-named) field reflectively.
     */
    private static final long JOIN_PROTECTION_BUDGET = 70L;

    private final Plugin plugin;

    public DeathRaceSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(SAVE_KEY);
        h.expect(DEAD_KEY);

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library library;
        try {
            Path root = Files.createTempDirectory("se-deathrace-suite");
            write(root, "enchants/deathrace-save.yml", SAVE);
            write(root, "enchants/deathrace-late.yml", LATE);
            library = LibraryLoader.load(root, compiler, 0);
            if (library.hasErrors()) {
                failBoth(h, "deathrace content failed to compile: " + library.diagnostics());
                return;
            }
        } catch (IOException e) {
            failBoth(h, "content staging: " + e);
            return;
        }

        ContentHolder holder = new ContentHolder(library);
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        ItemViewCache itemViews = new ItemViewCache(codec, library.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornStateStore worn = new WornStateStore(
                new WornResolver(Stores.equip(), itemViews, triggers.count(), triggers.attackTriggers(), triggers.defenseTriggers())::resolve);
        CombatRig rig = new CombatRig(plugin);
        DeathProbe deaths = rig.listen(new DeathProbe());
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE,
                (key, ability, ctx) -> { });
        AtomicLong tick = new AtomicLong();
        engine.sink.SinkEnv env = engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, engine.stores.EngineStores.fresh(), tick::incrementAndGet);
        CombatDispatch dispatch = new CombatDispatch(executor, dsEnv -> new engine.sink.ModernDispatchSink(handles, dsEnv), Stores.probe(), holder, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), -1, -1,
                actor -> java.util.Optional.empty(), env, CombatDispatch.Caps.unlimited(), Stores.projectiles());
        rig.listen(new CombatListener(dispatch));

        ItemStack saveBoots = new ItemStack(Material.LEATHER_BOOTS);
        codec.write(saveBoots, new CombatState(Map.of("enchants/deathrace-save", 1), List.of()));
        ItemStack lateBoots = new ItemStack(Material.LEATHER_BOOTS);
        codec.write(lateBoots, new CombatState(Map.of("enchants/deathrace-late", 1), List.of()));

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        rig.onArena(at, () -> {
            Player attacker;
            Player saved;
            Player doomed;
            try {
                attacker = rig.spawnFake(world, "se_race_atk");
                saved = rig.spawnFake(world, "se_race_v1");
                doomed = rig.spawnFake(world, "se_race_v2");
            } catch (Throwable t) {
                failBoth(h, "fake player spawn: " + t);
                rig.teardown();
                return;
            }
            // All three actors sit within a few chunks of spawn ⇒ one region; each hop below is still routed
            // through the owning entity's scheduler. Health is staged AFTER the join-protection wait, so
            // peaceful regen during it cannot skew the bands.
            Scheduling.onRegionLater(at, JOIN_PROTECTION_BUDGET, () -> Scheduling.onEntity(saved, () -> {
                saved.getInventory().setBoots(saveBoots);
                worn.refresh(saved, library.snapshot());
                saved.setHealth(STAGED_HEALTH);
                saved.damage(HIT, attacker); // the whole pipeline — walks, credit, kill decision — is synchronous
                h.guard(SAVE_KEY, () -> {
                    if (deaths.count(saved.getUniqueId()) != 0) {
                        throw new IllegalStateException("the victim died: the same-hit heal missed the kill decision");
                    }
                    if (saved.isDead()) {
                        throw new IllegalStateException("victim isDead with no death event: half-dead state");
                    }
                    double health = saved.getHealth();
                    // Above staging proves the heal landed pre-decision; below staging+heal proves the hit landed.
                    if (health <= STAGED_HEALTH || health >= STAGED_HEALTH + 10.0) {
                        throw new IllegalStateException("health " + health + " outside the healed-then-hit band ("
                                + STAGED_HEALTH + ", " + (STAGED_HEALTH + 10.0) + ")");
                    }
                });

                Scheduling.onEntity(doomed, () -> {
                    doomed.getInventory().setBoots(lateBoots);
                    worn.refresh(doomed, library.snapshot());
                    doomed.setHealth(STAGED_HEALTH);
                    doomed.damage(HIT, attacker);
                    int diedNow = deaths.count(doomed.getUniqueId());
                    Scheduling.onRegionLater(at, LATE_BUDGET, () -> {
                        h.guard(DEAD_KEY, () -> {
                            if (diedNow != 1) {
                                throw new IllegalStateException("staging broke: expected the bare lethal hit to "
                                        + "kill exactly once, saw " + diedNow + " death event(s)");
                            }
                            if (!doomed.isDead() || doomed.getHealth() > 0.0) {
                                throw new IllegalStateException("the WAIT-1 heal revived the corpse: health="
                                        + doomed.getHealth() + " after a fired death event");
                            }
                            if (deaths.count(doomed.getUniqueId()) != 1) {
                                throw new IllegalStateException("death fired again after the late heal: "
                                        + deaths.count(doomed.getUniqueId()));
                            }
                        });
                        rig.teardown();
                    });
                });
            }));
        });
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    private static void failBoth(Harness h, String message) {
        h.fail(SAVE_KEY, message);
        h.fail(DEAD_KEY, message);
    }

    /** Per-victim {@link org.bukkit.event.entity.PlayerDeathEvent} counts; fires on the victim's region thread. */
    private static final class DeathProbe implements org.bukkit.event.Listener {
        private final Map<UUID, AtomicInteger> byPlayer = new ConcurrentHashMap<>();

        @org.bukkit.event.EventHandler
        public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
            byPlayer.computeIfAbsent(event.getEntity().getUniqueId(), id -> new AtomicInteger()).incrementAndGet();
        }

        int count(UUID id) {
            AtomicInteger c = byPlayer.get(id);
            return c == null ? 0 : c.get();
        }
    }
}
