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
 * {@code amount} arg and a mocked {@link Sink} records the emitted intent — so the effect's
 * behavior is verified with no server.
 */
class FlatDamageEffectTest {

    @Test
    void addsTheFlatAmountToTheDamageFold() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(2.0);

        Sink sink = mock(Sink.class);
        new FlatDamageEffect().run(ctx, sink);

        verify(sink).addFlatDamage(2.0);
        verifyNoMoreInteractions(sink);
    }
}
