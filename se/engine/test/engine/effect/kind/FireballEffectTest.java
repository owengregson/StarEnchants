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
 * typed {@code yield} arg and the firing actor, and a mocked {@link Sink} records the
 * emitted intent — so the effect's behavior is verified with no server.
 */
class FireballEffectTest {

    @Test
    void emitsOneFireballIntentFromTheActor() {
        Player actor = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.dbl("yield")).thenReturn(2.0);

        Sink sink = mock(Sink.class);
        new FireballEffect().run(ctx, sink);

        verify(sink).fireball(actor, 2.0);
        verifyNoMoreInteractions(sink);
    }
}
