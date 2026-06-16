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
 * actor + typed title args, and a mocked {@link Sink} records the title intent —
 * verified with no server.
 */
class TitleEffectTest {

    @Test
    void emitsTitleIntentForTheActorWithTimings() {
        Player actor = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("title")).thenReturn("&cCRIT");
        when(ctx.str("subtitle")).thenReturn("&7nice");
        when(ctx.integer("fadeIn")).thenReturn(10);
        when(ctx.integer("stay")).thenReturn(40);
        when(ctx.integer("fadeOut")).thenReturn(10);

        Sink sink = mock(Sink.class);
        new TitleEffect().run(ctx, sink);

        verify(sink).title(actor, "&cCRIT", "&7nice", 10, 40, 10);
        verifyNoMoreInteractions(sink);
    }
}
