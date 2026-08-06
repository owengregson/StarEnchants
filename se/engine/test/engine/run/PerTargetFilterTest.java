package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.cond.VarBinding;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.StableKeyIndex;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import engine.condition.BuiltinVars;
import engine.condition.CrystalCounts;
import engine.condition.EnchantLevels;
import engine.condition.FactBuffer;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.sink.ModernDispatchSink;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.SelectorSpec;
import engine.spec.T;
import engine.stores.CooldownStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.grammar.expr.Cmp;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * The per-target subject pass (ADR-0076): the cursor's re-bind, the {@code each-if}/{@code each-chance} filter
 * and its shared draw, {@code each-cooldown}'s per-target window, and the {@code %selected%} count.
 *
 * <p>Runs the real executor, pipeline and sink over test-owned kinds, because the contracts here are ABOUT the
 * executor's list handling — which target the cursor is pointed at, in what order, and what survives.
 */
class PerTargetFilterTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    private static final int ENCHANT_SCOPE = 4;
    private static final StableKeyIndex KEYS = new StableKeyIndex(List.of("enchants/test/1"));

    /** {@code %selected%}'s slot, read from the same vocabulary the executor publishes into. */
    private static final int SELECTED =
            BuiltinVars.vocabulary().lookup(null, "selected").map(VarBinding::slot).orElseThrow();

    private RuntimeHandles handles;
    private CooldownStore cooldowns;
    private ActivationPipeline pipeline;
    private AbilityExecutor executor;
    private final Seen seen = new Seen();
    private List<LivingEntity> pool = List.of();
    private List<Location> locationPool = List.of();

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        cooldowns = new CooldownStore();
        pipeline = new ActivationPipeline(cooldowns, SoulSpender.NONE);
        EffectRegistry effects = EffectRegistry.builder()
                .register(new RecordingEffect(seen))
                .register(new SlotlessEffect())
                .build();
        SelectorRegistry selectors = SelectorRegistry.builder()
                .register(new PoolSelector(() -> pool, () -> locationPool))
                .build();
        executor = new AbilityExecutor(effects, selectors, pipeline, AreaScan.NONE);
    }

    // ── the cursor ──

    @Test
    void aSubjectReadAnswersForTheBoundBodyAndNotForTheVictim() {
        // The transposition catch: victim and subject deliberately carry DIFFERENT levels, so a filter wired to
        // the victim binding instead of the cursor would keep exactly the wrong bodies.
        LivingEntity immune = body();
        LivingEntity exposed = body();
        pool = List.of(immune, exposed);
        FactBuffer facts = facts();
        facts.enchantLevels(levels(id -> id.equals(immune.getUniqueId()) ? 3 : 0, 9));

        run(filtered(subjectLevel("poltergeist", Cmp.EQ, 0)), facts, () -> 0.0, 0L);

        // The victim reads 9 everywhere, so a victim-bound filter would have dropped BOTH bodies.
        assertEquals(List.of(exposed), seen.targets);
    }

    @Test
    void theCrystalAndVarSubjectReadsFollowTheSameCursor() {
        LivingEntity socketed = body();
        LivingEntity bare = body();
        pool = List.of(socketed, bare);
        FactBuffer facts = facts();
        facts.crystalCounts(counts(id -> id.equals(socketed.getUniqueId()) ? 2 : 0));

        run(filtered(new Cond.NumCmp(new NumExpr.CrystalCount(NumExpr.Scope.TARGET, "ranger"),
                Cmp.GE, new NumExpr.Lit(2))), facts, () -> 0.0, 0L);

        assertEquals(List.of(socketed), seen.targets);
    }

    // ── the shared draw ──

    @Test
    void aFilterAndItsComplementPartitionOverOneDrawPerBody() {
        // The whole point of %target.roll%: two rows that read ONE draw per body cannot both keep a target and
        // cannot both drop it — unlike the two independent ability-level rolls the shipped corpus has to use.
        LivingEntity a = body();
        LivingEntity b = body();
        LivingEntity c = body();
        pool = List.of(a, b, c);
        // The activation's roll supplier is gate 8's too, and gate 8 draws first — so the leading value is the
        // ability's own chance roll and the three after it are a's, b's and c's.
        double[] draws = {0.0, 5.0, 80.0, 12.4999};
        FactBuffer facts = facts();

        // each-chance: 12.5 — the FREEZE arm keeps roll < 12.5, the MESSAGE arm keeps the complement.
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .effects(recording(chanceUnder(12.5)), recording(chanceAtLeast(12.5)))
                .build();
        run(ability, facts, sequence(draws), 0L);

        List<LivingEntity> kept = seen.perEffect.get(0);
        List<LivingEntity> complement = seen.perEffect.get(1);
        assertEquals(pool.size(), kept.size() + complement.size(), "every body lands in exactly one arm");
        for (LivingEntity target : pool) {
            assertTrue(kept.contains(target) ^ complement.contains(target),
                    "one draw per body: never both arms, never neither");
        }
        assertEquals(List.of(a, c), kept, "5.0 and 12.4999 are under 12.5; 80.0 is not");
    }

    // ── copy-on-first-drop ──

    @Test
    void anUnfilteredPassKeepsTheSameListInstanceAndAFilteredOneKeepsOrder() {
        LivingEntity a = body();
        LivingEntity b = body();
        LivingEntity c = body();
        pool = List.of(a, b, c);

        run(filtered(alwaysTrue()), facts(), () -> 0.0, 0L);
        assertSame(pool, seen.slot, "nothing dropped → the resolved list itself, so an AoE allocates nothing");

        seen.reset();
        // Drop the MIDDLE body: the copy has to carry the ones already walked, in their authored order.
        FactBuffer facts = facts();
        facts.enchantLevels(levels(id -> id.equals(b.getUniqueId()) ? 1 : 0, 0));
        run(filtered(subjectLevel("poltergeist", Cmp.EQ, 0)), facts, () -> 0.0, 0L);
        assertEquals(List.of(a, c), seen.targets);
    }

    // ── staleness ──

    @Test
    void theCursorIsClearedBeforeTheNextEffectRuns() {
        // A later row reading %target.*% must see the UNBOUND zero, not whichever body the previous row's
        // filter happened to stop on — that would be an invisible dependency on filter order.
        LivingEntity only = body();
        pool = List.of(only);
        FactBuffer facts = facts();
        facts.enchantLevels(levels(id -> 7, 0));

        double[] observed = new double[1];
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .effects(recording(alwaysTrue()),
                        new CompiledEffect("PROBE_SUBJECT", Args.empty(), poolSelector(), 0, Affinity.AOE))
                .build();
        EffectRegistry probing = EffectRegistry.builder()
                .register(new RecordingEffect(seen))
                .register(new ProbeSubjectEffect(observed))
                .build();
        AbilityExecutor probe = new AbilityExecutor(probing,
                SelectorRegistry.builder().register(new PoolSelector(() -> pool, () -> locationPool)).build(),
                pipeline, AreaScan.NONE);
        probe.run(new Ability[] {ability}, new int[] {0}, activation(facts, () -> 0.0, 0L),
                context(mock(Player.class), null), sink(), KEYS);

        assertEquals(0.0, observed[0], "the second row must not inherit the first row's bound body");
    }

    // ── each-cooldown ──

    @Test
    void eachCooldownStampsTheSelectorTargetAndNotTheActivationVictim() {
        LivingEntity splashed = body();
        LivingEntity victim = body();
        pool = List.of(splashed);

        run(cooldownAbility(), facts(), () -> 0.0, 0L, victim);

        long scope = CooldownStore.key(compile.model.ScopeKinds.ENCHANT, ENCHANT_SCOPE, 2);
        assertTrue(cooldowns.remainingTicks(ACTOR, splashed.getUniqueId(), scope, 0L) > 0,
                "the splashed body carries the window");
        assertEquals(0L, cooldowns.remainingTicks(ACTOR, victim.getUniqueId(), scope, 0L),
                "the activation victim is a different body and must not be charged");
    }

    @Test
    void aStampedBodyIsDroppedInsideItsWindowAndAdmittedAfterIt() {
        LivingEntity target = body();
        pool = List.of(target);

        run(cooldownAbility(), facts(), () -> 0.0, 0L);
        assertEquals(List.of(target), seen.targets, "the first hit lands");

        seen.reset();
        run(cooldownAbility(), facts(), () -> 0.0, 10L);
        assertEquals(List.of(), seen.targets, "inside the 20-tick window the body is dropped");

        seen.reset();
        run(cooldownAbility(), facts(), () -> 0.0, 25L);
        assertEquals(List.of(target), seen.targets, "past the window it is admitted again");
    }

    @Test
    void aBodyDroppedByEachIfIsNeverStamped() {
        // The fixed order (each-if, then each-cooldown): a body a filter dropped must not be charged a window
        // for a hit it never took, or the NEXT activation would find it on cooldown for nothing.
        LivingEntity target = body();
        pool = List.of(target);
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .cooldownScope(ENCHANT_SCOPE, -1, -1)
                .effects(new CompiledEffect("RECORD", Args.empty(), poolSelector(), 0, Affinity.AOE, -1,
                        alwaysFalse(), new NumExpr.Lit(20)))
                .build();

        run(ability, facts(), () -> 0.0, 0L);

        long scope = CooldownStore.key(compile.model.ScopeKinds.ENCHANT, ENCHANT_SCOPE, 2);
        assertEquals(List.of(), seen.targets);
        assertEquals(0L, cooldowns.remainingTicks(ACTOR, target.getUniqueId(), scope, 0L),
                "a filtered-out body pays no window");
    }

    // ── %selected% ──

    @Test
    void selectedIsThePostFilterCountNotTheRawSelectorCount() {
        // The bug a raw-count implementation would ship: the refusal/summary lines exist to describe what
        // actually happened to bodies, and a filter is exactly what makes those two numbers differ.
        LivingEntity kept = body();
        LivingEntity dropped = body();
        pool = List.of(kept, dropped);
        FactBuffer facts = facts();
        facts.enchantLevels(levels(id -> id.equals(dropped.getUniqueId()) ? 1 : 0, 0));

        run(filtered(subjectLevel("poltergeist", Cmp.EQ, 0)), facts, () -> 0.0, 0L);

        assertEquals(1.0, facts.number(SELECTED));
    }

    @Test
    void selectedIsZeroedWhenAnAbilityBeginsItsWalkAndReadsMinusOneWhenItNeverActivated() {
        // R-QC67: "-1 = never activated" is a DIFFERENT answer from "0 = ran and matched nobody", and the
        // empty-selection refusal idiom rests entirely on being able to tell them apart.
        pool = List.of();
        FactBuffer facts = facts();
        facts.setNumber(SELECTED, 4); // a previous ability's count, which must not survive

        run(filtered(alwaysTrue()), facts, () -> 0.0, 0L);
        assertEquals(0.0, facts.number(SELECTED), "it activated and matched nobody");

        Ability wrongTrigger = Abilities.ability().trigger(TRIGGER + 1).affinity(Affinity.AOE)
                .effects(recording(null)).build();
        executor.run(new Ability[] {wrongTrigger}, new int[] {0}, activation(facts, () -> 0.0, 0L),
                context(mock(Player.class), null), sink(), KEYS);
        assertEquals(-1.0, facts.number(SELECTED), "it never activated at all");
    }

    @Test
    void anEffectWithNoTargetSlotLeavesSelectedUntouched() {
        pool = List.of(body(), body());
        FactBuffer facts = facts();
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .effects(recording(null),
                        new CompiledEffect("SLOTLESS", Args.empty(), CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL))
                .build();

        run(ability, facts, () -> 0.0, 0L);

        assertEquals(2.0, facts.number(SELECTED), "the slot-less row must not overwrite the payload's count");
    }

    @Test
    void aLocationResolvingSelectorPublishesTheLocationCount() {
        pool = List.of();
        locationPool = List.of(mock(Location.class), mock(Location.class), mock(Location.class));
        FactBuffer facts = facts();

        run(filtered(null), facts, () -> 0.0, 0L);

        assertEquals(3.0, facts.number(SELECTED));
    }

    // ── fixtures ──

    private void run(Ability ability, FactBuffer facts, DoubleSupplier roll, long now) {
        run(ability, facts, roll, now, null);
    }

    private void run(Ability ability, FactBuffer facts, DoubleSupplier roll, long now, LivingEntity victim) {
        executor.run(new Ability[] {ability}, new int[] {0}, activation(facts, roll, now, victim),
                context(mock(Player.class), victim), sink(), KEYS);
    }

    private ModernDispatchSink sink() {
        return new ModernDispatchSink(handles, Envs.sink().build());
    }

    private static Activation activation(FactBuffer facts, DoubleSupplier roll, long now) {
        return activation(facts, roll, now, null);
    }

    private static Activation activation(FactBuffer facts, DoubleSupplier roll, long now, LivingEntity victim) {
        Activation.Builder b = Activation.builder(ACTOR, 0, TRIGGER, now).facts(facts).chanceRoll(roll);
        if (victim != null) {
            b.victimId(victim.getUniqueId());
        }
        return b.build();
    }

    private static ActivationContext context(Player actor, LivingEntity victim) {
        return new ActivationContext(actor, victim, null, null);
    }

    private static FactBuffer facts() {
        return BuiltinVars.vocabulary().newFactBuffer();
    }

    private static LivingEntity body() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }

    /** A worn-enchant reader whose SUBJECT answer is keyed by id and whose victim answer is a fixed decoy. */
    private static EnchantLevels levels(java.util.function.ToIntFunction<UUID> bySubject, int victimLevel) {
        return new EnchantLevels() {
            @Override
            public int actorLevel(String key) {
                return 0;
            }

            @Override
            public int victimLevel(String key) {
                return victimLevel;
            }

            @Override
            public int levelOf(UUID id, String key) {
                return bySubject.applyAsInt(id);
            }
        };
    }

    private static CrystalCounts counts(java.util.function.ToIntFunction<UUID> bySubject) {
        return new CrystalCounts() {
            @Override
            public int actorCount(String key) {
                return 0;
            }

            @Override
            public int victimCount(String key) {
                return 0;
            }

            @Override
            public int countOf(UUID id, String key) {
                return bySubject.applyAsInt(id);
            }
        };
    }

    /** Draws the given values in order, then repeats the last — a seeded stand-in for the activation's roll. */
    private static DoubleSupplier sequence(double... values) {
        int[] index = {0};
        return () -> values[Math.min(index[0]++, values.length - 1)];
    }

    private static Ability filtered(Cond eachCondition) {
        return Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .effects(recording(eachCondition)).build();
    }

    private Ability cooldownAbility() {
        return Abilities.ability().trigger(TRIGGER).affinity(Affinity.AOE)
                .cooldownScope(ENCHANT_SCOPE, -1, -1)
                .effects(new CompiledEffect("RECORD", Args.empty(), poolSelector(), 0, Affinity.AOE, -1,
                        null, new NumExpr.Lit(20)))
                .build();
    }

    private static CompiledEffect recording(Cond eachCondition) {
        return new CompiledEffect("RECORD", Args.empty(), poolSelector(), 0, Affinity.AOE, -1,
                eachCondition, null);
    }

    private static CompiledSelector poolSelector() {
        return new CompiledSelector("POOL", Args.empty());
    }

    private static Cond alwaysTrue() {
        return new Cond.BoolLit(true);
    }

    private static Cond alwaysFalse() {
        return new Cond.BoolLit(false);
    }

    /** {@code each-chance: rate} as the lower stage desugars it — one draw, compared two ways. */
    private static Cond chanceUnder(double rate) {
        return new Cond.NumCmp(new NumExpr.SubjectNum(NumExpr.SubjectFact.ROLL), Cmp.LT, new NumExpr.Lit(rate));
    }

    private static Cond chanceAtLeast(double rate) {
        return new Cond.NumCmp(new NumExpr.SubjectNum(NumExpr.SubjectFact.ROLL), Cmp.GE, new NumExpr.Lit(rate));
    }

    private static Cond subjectLevel(String key, Cmp op, double value) {
        return new Cond.NumCmp(new NumExpr.EnchantLevel(NumExpr.Scope.TARGET, key), op, new NumExpr.Lit(value));
    }

    /** What each effect run saw, per row and flattened — the observable the filter is asserted through. */
    private static final class Seen {
        private final List<LivingEntity> targets = new ArrayList<>();
        private final List<List<LivingEntity>> perEffect = new ArrayList<>();
        private List<LivingEntity> slot;

        void reset() {
            targets.clear();
            perEffect.clear();
            slot = null;
        }
    }

    private static final class RecordingEffect implements EffectKind {
        private static final EffectSpec SPEC = EffectSpec.of("RECORD")
                .target("who", T.AOE).affinity(Affinity.AOE)
                .doc("test double").example("{ RECORD: { } }").build();

        private final Seen seen;

        RecordingEffect(Seen seen) {
            this.seen = seen;
        }

        @Override
        public EffectSpec spec() {
            return SPEC;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run(EffectCtx ctx, Sink sink) {
            List<LivingEntity> row = new ArrayList<>();
            Iterable<LivingEntity> bound = ctx.targets("who");
            bound.forEach(row::add);
            seen.slot = bound instanceof List ? (List<LivingEntity>) bound : null;
            seen.targets.addAll(row);
            seen.perEffect.add(row);
        }
    }

    /** Declares no target slot at all — the one shape that must leave {@code %selected%} alone. */
    private static final class SlotlessEffect implements EffectKind {
        private static final EffectSpec SPEC = EffectSpec.of("SLOTLESS").affinity(Affinity.CONTEXT_LOCAL)
                .doc("test double").example("{ SLOTLESS: { } }").build();

        @Override
        public EffectSpec spec() {
            return SPEC;
        }

        @Override
        public void run(EffectCtx ctx, Sink sink) {
            // nothing — its whole contract is not touching the count
        }
    }

    /** Reads {@code %target.enchlevel.*%} from inside a LATER row, to prove the cursor was unbound. */
    private static final class ProbeSubjectEffect implements EffectKind {
        private static final EffectSpec SPEC = EffectSpec.of("PROBE_SUBJECT")
                .param("level", schema.spec.D.DOUBLE.def(0))
                .target("who", T.AOE).affinity(Affinity.AOE)
                .doc("test double").example("{ PROBE_SUBJECT: { } }").build();

        private final double[] observed;

        ProbeSubjectEffect(double[] observed) {
            this.observed = observed;
        }

        @Override
        public EffectSpec spec() {
            return SPEC;
        }

        @Override
        public void run(EffectCtx ctx, Sink sink) {
            observed[0] = ctx.dbl("level");
        }
    }

    /** Resolves whatever the test staged, entities and locations both. */
    private static final class PoolSelector implements SelectorKind {
        private static final SelectorSpec SPEC = SelectorSpec.of("POOL")
                .doc("test double").example("@Pool").build();

        private final java.util.function.Supplier<List<LivingEntity>> entities;
        private final java.util.function.Supplier<List<Location>> locations;

        PoolSelector(java.util.function.Supplier<List<LivingEntity>> entities,
                     java.util.function.Supplier<List<Location>> locations) {
            this.entities = entities;
            this.locations = locations;
        }

        @Override
        public SelectorSpec spec() {
            return SPEC;
        }

        @Override
        public List<LivingEntity> resolve(SelectorCtx ctx) {
            return entities.get();
        }

        @Override
        public List<Location> resolveLocations(SelectorCtx ctx) {
            return locations.get();
        }
    }

    @Test
    void unfilteredEffectsNeverPayForACursorTheyDoNotUse() {
        // An effect that opts into nothing must reach its kind with the plain resolved list — the null check
        // the perf discipline promises, not a wrapper.
        pool = List.of(body(), body());
        run(filtered(null), facts(), () -> 0.0, 0L);
        assertSame(pool, seen.slot);
        assertFalse(seen.targets.isEmpty());
    }
}
