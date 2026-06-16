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
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * typed text and the activating actor, and a mocked {@link Sink} records the emitted
 * intent — so the effect's behavior is verified with no server. This follows the
 * template every effect kind's unit test uses.
 */
class ActionBarEffectTest {

    @Test
    void emitsOneActionBarIntentForTheActor() {
        Player actor = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("text")).thenReturn("&eCharged");

        Sink sink = mock(Sink.class);
        new ActionBarEffect().run(ctx, sink);

        verify(sink).actionBar(actor, "&eCharged");
        verifyNoMoreInteractions(sink);
    }
}
