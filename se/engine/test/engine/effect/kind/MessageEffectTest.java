package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the typed
 * {@code text} arg and the activating actor, and a mocked {@link Sink} records the emitted
 * intent — so the effect's behavior is verified with no server. Follows the effect-kind
 * unit-test template.
 */
class MessageEffectTest {

    @Test
    void emitsOneMessageIntentToTheActor() {
        Player actor = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("text")).thenReturn("hi");

        Sink sink = mock(Sink.class);
        new MessageEffect().run(ctx, sink);

        verify(sink).message(actor, "hi");
        verifyNoMoreInteractions(sink);
    }
}
