package engine.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.effect.kind.BuiltinEffects;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.TargetSpec;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import testfx.FakeEffectCtx;
import testfx.SpecDrivenCtx;

/**
 * The drift guard the per-kind tests could never be: instead of hand-typing each kind's param names into a
 * test (the names then live in two places and silently drift), drive every registered kind from its OWN
 * {@link EffectSpec}. A ctx carrying exactly the declared params runs the kind; reading any param the spec
 * does not declare trips the strict {@link FakeEffectCtx}. So a renamed or mistyped param can no longer pass
 * its test by coincidence — the spec is the single source of the names.
 */
class SpecConformanceTest {

    /**
     * SUPPRESS is the one kind whose args the erase stage REWRITES before runtime ({@code scope}/{@code key}
     * String → long, the bridge invariant in {@code DefaultEraseStage.eraseSuppressArgs}). So at {@code run()}
     * it reads its scope/key as the post-erasure integers, not the authored ENUM/STRING the spec declares —
     * the only kind whose runtime read types differ from its spec. It still gets the round-trip checks; only
     * the spec-typed run is skipped.
     */
    private static final Set<String> ERASE_REWRITTEN = Set.of("SUPPRESS");

    @Test
    void theRegistryIsNotEmpty() {
        assertFalse(BuiltinEffects.registry().kinds().isEmpty());
    }

    @TestFactory
    Stream<DynamicTest> everyKindRoundTripsAndReadsOnlyItsDeclaredParams() {
        EffectRegistry registry = BuiltinEffects.registry();
        return registry.kinds().stream().map(kind -> DynamicTest.dynamicTest(kind.head(), () -> {
            EffectSpec spec = kind.spec();

            // the head is canonical and round-trips through the registry; the compiler bridge keys validation
            // on the param-spec head, so it must equal the kind head.
            assertEquals(spec.head().toUpperCase(Locale.ROOT), spec.head(), "head must be canonical upper-case");
            assertSame(kind, registry.lookup(spec.head()).orElseThrow(), "head must round-trip in the registry");
            assertEquals(spec.head(), spec.paramSpec().head(), "param-spec head must match the kind head");

            if (ERASE_REWRITTEN.contains(spec.head())) {
                return; // its run() reads erase-rewritten arg types, not the authored spec types
            }

            // a ctx with ONLY the declared scalars; the actor/targets/location are supplied so the body runs.
            FakeEffectCtx ctx = SpecDrivenCtx.scalars(spec);
            Player p = mock(Player.class);
            Location loc = mock(Location.class);
            when(p.getLocation()).thenReturn(loc);
            when(p.getUniqueId()).thenReturn(UUID.randomUUID());
            ctx.actor(p).victim(p).location(loc).activeGem(UUID.randomUUID());
            for (TargetSpec t : spec.targets()) {
                ctx.targets(t.name(), p);   // a player satisfies both entity and player gates
                ctx.locations(t.name(), loc); // and a location for block/coordinate selectors
            }
            Sink sink = mock(Sink.class);

            assertDoesNotThrow(() -> kind.run(ctx, sink),
                    spec.head() + ".run read a parameter its EffectSpec does not declare");
            assertDoesNotThrow(() -> kind.stop(ctx, sink),
                    spec.head() + ".stop read a parameter its EffectSpec does not declare");
        }));
    }
}
