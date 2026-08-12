package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.FactMask;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.boot.ContentCompiler;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AbilityQuarantine;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.run.FactPopulator;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkReadback;
import engine.sink.SoulDebit;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import engine.trigger.BuiltinTriggers;
import feature.trigger.TriggerRunner;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * Executes EVERY ability of the shipped cosmic pack once, on every matrix target. {@link CatalogSuite} proves the
 * pack COMPILES against this version's real handle registry; nothing proved its abilities RUN — the ~1800 authored
 * units shipped without a single one ever having been executed by a test on any version, so a null target slot, a
 * mistyped param read or a kind that faults on one era's API reached players first.
 *
 * <p><strong>Force-executed, not gated.</strong> The walk goes through
 * {@link AbilityExecutor#runForced} one dense id at a time: it deliberately bypasses chance, cooldown, condition
 * and soul debit, so an ability authored at 3% behind a {@code %combo% >= 5} gate still runs here — which is the
 * only way a smoke pass reaches all of them. A real {@link AbilityQuarantine} at threshold 1 is bound, built from
 * the library's own {@code SourceMap}/{@code StableKeyIndex}, so one fault names the authored {@code file:line}
 * and {@link AbilityExecutor#quarantinedKeys()} is the whole failure channel: non-empty is the failure.
 *
 * <p><strong>Emit, never flush.</strong> The sink's deferred intents are dropped rather than flushed. Two reasons,
 * and both are decisive: a flushed intent runs inside {@code DispatchPlan}'s own warn-and-skip batch, so a fault
 * there could never reach the quarantine this suite reads — flushing would add no signal — and the harness launches
 * all ~60 scenarios into ONE shared arena on one shared {@code DEADLINE_TICKS} budget, where ~1800 abilities' worth
 * of real explosions, kills and block edits is cross-suite contamination plus a certain timeout. What a kind does
 * to the WORLD is {@link AffinityAutogenSuite}'s job (one ability per kind, fired and flushed); what every authored
 * ability does at emit time — args, expressions, selector resolution, handle reads — is this one's.
 *
 * <p><strong>Raw message tokens, intercepted live.</strong> The {@link SinkReadback} handed to the executor is a
 * proxy that scans the text reaching {@code message}/{@code messageTo}/{@code actionBar}/{@code title} before
 * delegating. That is the live seam the brief prefers, and it beats scanning authored strings statically: by the
 * time a line hits the sink, {@code MESSAGE} has already substituted {@code {ATTACKER}}/{@code {VICTIM}}, its
 * {@code tokens} bindings and the per-recipient {@code {SELF}}/{@code {RELATION_COLOR}}, so anything of the form
 * {@code {NAME}} still standing is by construction unbound — no binder set to re-derive and no false positive from
 * a token the effect fills per copy. It is a reflection proxy only because {@code SinkReadback} carries ~150
 * methods and the one concrete impl is final, so neither a wrapper nor a subclass is available. A {@code %var%}
 * naming a real condition variable is reported the same way; {@code {#RRGGBB}} is skipped, being a colour
 * {@code Colors.translate} owns. A {@code who: @Victim} line addressed to the cow is never sent (chat needs a
 * player recipient) and so is not scanned.
 */
public final class ShippedPackExecutionSuite implements Harness.Scenario {

    private static final String BUNDLE = "pack-cosmic";
    private static final String RUNS = "shippedpack.everyAbilityRuns";
    private static final String TOKENS = "shippedpack.noRawMessageTokens";

    /**
     * Abilities forced per scheduled tick. The {@link Harness} fails every unresolved check at
     * {@code DEADLINE_TICKS} (400) and every other scenario is spending that same budget, so the walk has to cost
     * a small slice of it: 128 clears ~1800 abilities in ~15 ticks. It is also the per-tick ceiling — a busy
     * combat tick walks a handful of abilities, so 128 emit-only walks is a heavy tick, not a stalled one.
     */
    private static final int SLICE = 128;

    /** Failure detail is capped so one bad content wave can't bury the results file. */
    private static final int REPORTED = 15;

    /** Stop recording offending lines well before a systemic regression exhausts memory. */
    private static final int RECORD_LIMIT = 200;

    /** The chat-facing sink methods — the only ones whose String args are player-visible copy. */
    private static final Set<String> CHAT_METHODS = Set.of("message", "messageTo", "actionBar", "title");

    private static final Pattern BRACE_TOKEN = Pattern.compile("\\{[A-Za-z_][A-Za-z0-9_-]*}");
    private static final Pattern PERCENT_VAR = Pattern.compile("%([A-Za-z][A-Za-z0-9._]*)%");

    /** The condition vocabulary, so a stray {@code 50%} in copy is not mistaken for an unsubstituted variable. */
    private static final VarVocabulary VOCABULARY = BuiltinVars.vocabulary();

    private final Plugin plugin;

    public ShippedPackExecutionSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect(RUNS);
        h.expect(TOKENS);

        // The retained resolver pair, as in CatalogSuite/AffinityAutogenSuite: the compile-time handle interning
        // and the runtime lookup must read one registry, or an id resolved here means something else at the sink.
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);

        Library library;
        try {
            Path content = BundledContent.extract(BUNDLE);
            library = LibraryLoader.load(content, compiler, 0);
        } catch (IOException | RuntimeException e) {
            failBoth(h, "could not load the bundled " + BUNDLE + ": " + e);
            return;
        }
        if (library.hasErrors()) {
            failBoth(h, BUNDLE + " did not compile (CatalogSuite owns this): " + library.diagnostics());
            return;
        }
        if (library.snapshot().abilityCount() == 0) {
            failBoth(h, BUNDLE + " compiled to zero abilities — did the content bundle?");
            return;
        }
        new Walk(h, library.snapshot(), new RuntimeHandles(resolvers)).start();
    }

    private static void failBoth(Harness h, String reason) {
        h.fail(RUNS, reason);
        h.fail(TOKENS, reason);
    }

    /**
     * Drives the sliced walk over one staged actor/victim pair. The actor is a clientless fake player and the
     * victim a cow (the matrix worlds are peaceful, so a cow is the standard live-suite mob); both sit on the
     * force-loaded spawn chunk, and every slice runs on the ACTOR's scheduler, which is its owning region's
     * thread on Folia and follows it if it ever moves.
     */
    private final class Walk {

        private final Harness h;
        private final Ability[] abilities;
        private final StableKeyIndex stableKeys;
        private final RuntimeHandles handles;
        private final AbilityExecutor executor;
        private final SinkEnv env;
        private final FactPopulator facts;
        private final CombatRig rig = new CombatRig(plugin);
        private final AtomicLong ticks = new AtomicLong();
        private final World world;
        private final Location at;
        private final int worldId;
        private final int triggerId;

        // One reused candidate array: runForced takes ids in bulk, but feeding it one at a time is what lets a
        // scanned chat line name the ability that emitted it.
        private final int[] candidate = new int[1];
        private final Set<String> rawLines = new LinkedHashSet<>();

        private Player actor;
        private LivingEntity victim;
        private String currentKey;

        private Walk(Harness h, Snapshot snapshot, RuntimeHandles handles) {
            this.h = h;
            this.abilities = snapshot.abilities();
            this.stableKeys = snapshot.stableKeys();
            this.handles = handles;
            this.executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                    new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), areaScan());
            // Threshold 1: this pass runs each ability exactly once, so "faulted" and "quarantined" must coincide.
            executor.bindQuarantine(new AbilityQuarantine(snapshot.sourceMap(), stableKeys, 1));
            this.env = SinkEnv.of(EconomyService.NONE, SoulDebit.NONE, EngineStores.fresh(),
                    ticks::incrementAndGet);
            this.facts = new FactPopulator(BuiltinVars.vocabulary(), Stores.probe());
            this.world = plugin.getServer().getWorlds().get(0);
            this.at = world.getSpawnLocation();
            this.worldId = TriggerRunner.worldId(snapshot, world);
            this.triggerId = BuiltinTriggers.registry().idOf("ATTACK").orElseThrow();
        }

        void start() {
            rig.onArena(at, () -> {
                try {
                    actor = rig.spawnFake(world, "se_shipped_atk");
                    victim = rig.spawn(world, at, EntityType.COW, LivingEntity.class);
                    victim.setAI(false); // pin it beside the actor so every slice reads one region
                } catch (Throwable t) {
                    failBoth(h, "staging: " + t);
                    rig.teardown();
                    return;
                }
                plugin.getLogger().info("[shipped-pack] forcing " + abilities.length + " abilities in slices of "
                        + SLICE);
                Scheduling.onEntity(actor, () -> slice(0));
            });
        }

        private void slice(int from) {
            int to = Math.min(from + SLICE, abilities.length);
            try {
                // Rebuilt per slice, on whichever thread this slice landed on: the populator hands back a
                // thread-local buffer, and the actor's location is only safe to read here. The cow rides as
                // BOTH victim and attacker so a defence-side ability reads a body rather than a null.
                ActivationContext context = new ActivationContext(actor, victim, victim, actor.getLocation());
                Activation activation = Activation.builder(actor.getUniqueId(), worldId, triggerId,
                                ticks.get())
                        // The random-backed roll every production entry point installs: a gateless run still
                        // draws per body for the per-target filters (R-QC25c).
                        .chanceRoll(() -> ThreadLocalRandom.current().nextDouble() * 100.0)
                        .facts(facts.populate(context, ticks.get(), FactMask.ALL))
                        .location(context.location())
                        .victimId(victim.getUniqueId())
                        .build();
                for (int id = from; id < to; id++) {
                    currentKey = stableKeys.keyOf(id);
                    candidate[0] = id;
                    executor.runForced(abilities, candidate, activation, context,
                            scanning(new ModernDispatchSink(handles, env)), stableKeys);
                }
            } catch (Throwable t) {
                failBoth(h, "slice [" + from + "," + to + ") threw out of the walk: " + t);
                rig.teardown();
                return;
            }
            if (to < abilities.length) {
                Scheduling.onEntityLater(actor, 1L, () -> slice(to));
            } else {
                report();
            }
        }

        private void report() {
            List<String> quarantined = executor.quarantinedKeys();
            if (quarantined.isEmpty()) {
                h.pass(RUNS);
            } else {
                h.fail(RUNS, quarantined.size() + " of " + abilities.length
                        + " shipped abilities faulted during execution: " + capped(quarantined));
            }
            if (rawLines.isEmpty()) {
                h.pass(TOKENS);
            } else {
                h.fail(TOKENS, rawLines.size() + " chat line(s) reached the sink with an unsubstituted token: "
                        + capped(List.copyOf(rawLines)));
            }
            rig.teardown();
        }

        /** {@code real} with every chat-facing String argument scanned on the way through. */
        private SinkReadback scanning(SinkReadback real) {
            return (SinkReadback) Proxy.newProxyInstance(SinkReadback.class.getClassLoader(),
                    new Class<?>[]{SinkReadback.class}, (proxy, method, args) -> {
                        if (args != null && CHAT_METHODS.contains(method.getName())) {
                            for (Object arg : args) {
                                if (arg instanceof String line) {
                                    record(line);
                                }
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException wrapped) {
                            throw wrapped.getCause(); // the executor's per-ability catch must see the real fault
                        }
                    });
        }

        private void record(String line) {
            if (line == null || rawLines.size() >= RECORD_LIMIT) {
                return;
            }
            String token = firstRawToken(line);
            if (token != null) {
                rawLines.add(currentKey + " left " + token + " in \"" + line + "\"");
            }
        }
    }

    /** Nearby-living only: the staged pair is what an AoE selector should find, and no other scan is stageable. */
    private static AreaScan areaScan() {
        return (center, radius) -> {
            World world = center.getWorld();
            List<LivingEntity> living = new ArrayList<>();
            if (world != null) {
                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof LivingEntity found) {
                        living.add(found);
                    }
                }
            }
            return living;
        };
    }

    /** The first token this line should have had substituted, or {@code null} when it is fully rendered. */
    private static String firstRawToken(String line) {
        Matcher brace = BRACE_TOKEN.matcher(line);
        if (brace.find()) {
            return brace.group();
        }
        Matcher var = PERCENT_VAR.matcher(line);
        while (var.find()) {
            if (VOCABULARY.bindings().containsKey(var.group(1).toLowerCase(Locale.ROOT))) {
                return var.group();
            }
        }
        return null;
    }

    private static String capped(List<String> entries) {
        String shown = String.join(" | ", entries.subList(0, Math.min(REPORTED, entries.size())));
        return entries.size() > REPORTED ? shown + " | … (" + entries.size() + " total)" : shown;
    }
}
