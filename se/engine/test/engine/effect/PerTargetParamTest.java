package engine.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.effect.kind.BuiltinEffects;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.TargetSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import schema.spec.Args;
import schema.spec.Param;
import schema.spec.ParamType;
import testfx.FakeEffectCtx;
import testfx.SpecDrivenCtx;

/**
 * The registry-walking gate that makes {@code D.DOUBLE.perTarget()} load-bearing rather than decorative
 * (ADR-0076). A numeric argument is a compiled expression re-evaluated on every {@code ctx.dbl(...)}, so
 * WHERE a kind reads it decides whether {@code rand(0.5, 5.5)} draws once for a whole chain or once per body.
 * The flag declares the answer; this counts the reads and fails the build when the two drift apart.
 *
 * <p>Counted by DIFFERENCE (one target vs two) rather than by an absolute number, so a param a kind only
 * reads in one branch — or reads twice for its own reasons — is still judged on the one thing that matters:
 * whether its reads scale with the target list.
 */
class PerTargetParamTest {

    /** Their {@code run()} reads erase-rewritten arg types, so a spec-typed ctx cannot drive them. */
    private static final Set<String> ERASE_REWRITTEN = Set.of("SUPPRESS", "SUPPRESS_INCOMING");

    @TestFactory
    Stream<DynamicTest> everyNumericParamReadsPerTargetIfAndOnlyIfItSaysSo() {
        return BuiltinEffects.registry().kinds().stream()
                .filter(kind -> !ERASE_REWRITTEN.contains(kind.head()))
                .map(kind -> DynamicTest.dynamicTest(kind.head(), () -> {
                    EffectSpec spec = kind.spec();
                    Map<String, Integer> one = countReads(kind, spec, 1);
                    Map<String, Integer> two = countReads(kind, spec, 2);
                    for (Param param : spec.paramSpec().params()) {
                        if (!isNumeric(param.type()) || param.type().isHoisted()) {
                            continue; // a string/enum read re-evaluates nothing; a hoisted knob never reaches run()
                        }
                        int single = one.getOrDefault(param.name(), 0);
                        int pair = two.getOrDefault(param.name(), 0);
                        String where = spec.head() + '.' + param.name();
                        if (param.type().isPerTarget()) {
                            assertTrue(single >= 1, where + " declares perTarget() but is never read");
                            assertEquals(single * 2, pair,
                                    where + " declares perTarget() but its read is hoisted above the loop");
                        } else {
                            assertEquals(single, pair,
                                    where + " is read once per target but does not declare perTarget()");
                        }
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> onlyAnEntityTargetingKindCanDeclareAPerTargetParam() {
        // A per-target read needs a target loop to sit in; declaring one on a slot-less kind would be a flag
        // that can never fire, and the counting gate above would pass it vacuously.
        return BuiltinEffects.registry().kinds().stream()
                .filter(kind -> kind.spec().paramSpec().anyPerTarget())
                .map(kind -> DynamicTest.dynamicTest(kind.head(),
                        () -> assertFalse(kind.spec().targets().isEmpty(),
                                kind.head() + " declares a perTarget() param but no target slot")));
    }

    /** Run {@code kind} against {@code targetCount} bodies, returning how often each param was read. */
    private static Map<String, Integer> countReads(EffectKind kind, EffectSpec spec, int targetCount) {
        FakeEffectCtx delegate = SpecDrivenCtx.scalars(spec);
        Player actor = mock(Player.class);
        Location location = mock(Location.class);
        when(actor.getLocation()).thenReturn(location);
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
        delegate.actor(actor).victim(actor).location(location).activeGem(UUID.randomUUID());
        Player[] bodies = new Player[targetCount];
        Location[] places = new Location[targetCount];
        for (int i = 0; i < targetCount; i++) {
            Player body = mock(Player.class);
            when(body.getUniqueId()).thenReturn(UUID.randomUUID());
            when(body.getLocation()).thenReturn(location);
            bodies[i] = body;
            places[i] = location;
        }
        for (TargetSpec slot : spec.targets()) {
            delegate.targets(slot.name(), bodies);
            delegate.locations(slot.name(), places);
        }
        Counting counting = new Counting(delegate);
        try {
            kind.run(counting, mock(Sink.class));
        } catch (RuntimeException tolerated) {
            // A kind may bail on a mocked world; the reads it DID make before that are still the contract, and
            // any kind that bails identically at one and two targets still compares cleanly.
        }
        return counting.reads;
    }

    private static boolean isNumeric(ParamType type) {
        ParamType.Kind kind = type.kind();
        return kind == ParamType.Kind.DOUBLE || kind == ParamType.Kind.INT || kind == ParamType.Kind.TICKS
                || kind == ParamType.Kind.EXPR_MAP;
    }

    /** Counts every numeric read by name and delegates the value, so kinds behave exactly as in production. */
    private static final class Counting implements EffectCtx {

        private final EffectCtx delegate;
        private final Map<String, Integer> reads = new HashMap<>();

        Counting(EffectCtx delegate) {
            this.delegate = delegate;
        }

        private void seen(String name) {
            reads.merge(name, 1, Integer::sum);
        }

        @Override
        public double dbl(String name) {
            seen(name);
            return delegate.dbl(name);
        }

        @Override
        public int integer(String name) {
            seen(name);
            return delegate.integer(name);
        }

        @Override
        public long lng(String name) {
            seen(name);
            return delegate.lng(name);
        }

        @Override
        public Map<String, Double> numbers(String name) {
            seen(name);
            return delegate.numbers(name);
        }

        @Override
        public boolean bool(String name) {
            return delegate.bool(name);
        }

        @Override
        public String str(String name) {
            return delegate.str(name);
        }

        @Override
        public java.util.List<Integer> ids(String name) {
            return delegate.ids(name);
        }

        @Override
        public Args args() {
            return delegate.args();
        }

        @Override
        public Player actor() {
            return delegate.actor();
        }

        @Override
        public LivingEntity victim() {
            return delegate.victim();
        }

        @Override
        public LivingEntity attacker() {
            return delegate.attacker();
        }

        @Override
        public Location location() {
            return delegate.location();
        }

        @Override
        public Location actorOrigin() {
            return delegate.actorOrigin();
        }

        @Override
        public Location actorOriginEye() {
            return delegate.actorOriginEye();
        }

        @Override
        public Iterable<LivingEntity> targets(String selectorName) {
            return delegate.targets(selectorName);
        }

        @Override
        public Iterable<Location> targetLocations(String selectorName) {
            return delegate.targetLocations(selectorName);
        }

        @Override
        public int level() {
            return delegate.level();
        }

        @Override
        public int sourceDefId() {
            return delegate.sourceDefId();
        }

        @Override
        public int sourceGroup() {
            return delegate.sourceGroup();
        }

        @Override
        public int cooldownScope() {
            return delegate.cooldownScope();
        }

        @Override
        public int cooldownTicks() {
            return delegate.cooldownTicks();
        }

        @Override
        public UUID activeGem() {
            return delegate.activeGem();
        }
    }
}
