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
 * typed {@code command} arg and a mocked {@link Sink} records the emitted intent — so
 * the effect's behavior is verified with no server.
 */
class RunCommandEffectTest {

    @Test
    void emitsOneConsoleCommandIntent() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("command")).thenReturn("say hi");

        Sink sink = mock(Sink.class);
        new RunCommandEffect().run(ctx, sink);

        verify(sink).consoleCommand("say hi");
        verifyNoMoreInteractions(sink);
    }
}
