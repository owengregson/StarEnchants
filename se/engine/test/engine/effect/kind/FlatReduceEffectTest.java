package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * typed amount, and a mocked {@link Sink} records that the flat reduction is
 * contributed to the damage fold — verified with no server.
 */
class FlatReduceEffectTest {

    @Test
    void contributesAFlatReductionToTheFold() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(2.5);

        Sink sink = mock(Sink.class);
        new FlatReduceEffect().run(ctx, sink);

        verify(sink).addFlatReduction(2.5);
        verifyNoMoreInteractions(sink);
    }
}
