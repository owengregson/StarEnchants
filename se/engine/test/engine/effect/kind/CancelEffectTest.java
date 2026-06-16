package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} and a mocked
 * {@link Sink} verify the effect's behavior with no server — here, that running the
 * paramless {@code CANCEL} kind emits exactly one {@code cancelEvent} intent.
 */
class CancelEffectTest {

    @Test
    void emitsExactlyOneCancelEventIntent() {
        EffectCtx ctx = mock(EffectCtx.class);
        Sink sink = mock(Sink.class);

        new CancelEffect().run(ctx, sink);

        verify(sink).cancelEvent();
        verifyNoMoreInteractions(sink);
    }
}
