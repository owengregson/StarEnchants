package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds typed
 * args, and a mocked {@link Sink} records the emitted intents — so the effect's
 * behavior is verified with no server. {@code ADD_DAMAGE} converts the authored
 * percent into a fraction of the additive attack bucket (§6.1).
 */
class AddDamageEffectTest {

    @Test
    void addsOutgoingDamageAsFractionOfPercent() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("percent")).thenReturn(20.0);

        Sink sink = mock(Sink.class);
        new AddDamageEffect().run(ctx, sink);

        verify(sink).addOutgoingDamage(0.2);
        verifyNoMoreInteractions(sink);
    }
}
