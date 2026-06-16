package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the typed
 * {@code percent} arg and a mocked {@link Sink} records the emitted intent — so the
 * effect's behavior is verified with no server.
 */
class ReduceDamageEffectTest {

    @Test
    void contributesPercentAsFractionToDefenseBucket() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("percent")).thenReturn(15.0);

        Sink sink = mock(Sink.class);
        new ReduceDamageEffect().run(ctx, sink);

        verify(sink).addDamageReduction(0.15);
        verifyNoMoreInteractions(sink);
    }
}
