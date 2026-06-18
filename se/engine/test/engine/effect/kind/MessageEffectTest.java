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
 * Mock-host test (docs/architecture.md §1.3) for the canonical {@code MESSAGE}, which now routes by
 * {@code channel} (chat / actionbar / title) — collapsing the deleted ACTIONBAR and TITLE kinds. A
 * mocked {@link EffectCtx} feeds the typed args + the activating actor; a mocked {@link Sink} records
 * which intent the channel selected.
 */
class MessageEffectTest {

    @Test
    void chatChannelEmitsMessage() {
        Player actor = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("channel")).thenReturn("chat");
        when(ctx.str("text")).thenReturn("hi");

        Sink sink = mock(Sink.class);
        new MessageEffect().run(ctx, sink);

        verify(sink).message(actor, "hi");
        verifyNoMoreInteractions(sink);
    }

    @Test
    void actionbarChannelEmitsActionBar() {
        Player actor = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("channel")).thenReturn("actionbar");
        when(ctx.str("text")).thenReturn("charged");

        Sink sink = mock(Sink.class);
        new MessageEffect().run(ctx, sink);

        verify(sink).actionBar(actor, "charged");
        verifyNoMoreInteractions(sink);
    }

    @Test
    void titleChannelEmitsTitleWithTimings() {
        Player actor = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.str("channel")).thenReturn("title");
        when(ctx.str("text")).thenReturn("&cCRITICAL");
        when(ctx.str("subtitle")).thenReturn("&7you struck hard");
        when(ctx.integer("fadeIn")).thenReturn(10);
        when(ctx.integer("stay")).thenReturn(40);
        when(ctx.integer("fadeOut")).thenReturn(10);

        Sink sink = mock(Sink.class);
        new MessageEffect().run(ctx, sink);

        verify(sink).title(actor, "&cCRITICAL", "&7you struck hard", 10, 40, 10);
        verifyNoMoreInteractions(sink);
    }
}
