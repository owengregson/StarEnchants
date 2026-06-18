package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code REMOVE_SOULS}: it debits the activator's active gem when in soul mode, and is a
 * no-op otherwise. Exactly ONE debit intent is emitted (the dupe-risk subsystem — a single sink call).
 */
class RemoveSoulsEffectTest {

    @Test
    void debitsTheActivatorsActiveGem() {
        UUID gemId = UUID.randomUUID();
        Player holder = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.activeGem()).thenReturn(gemId);
        when(ctx.actor()).thenReturn(holder);
        when(ctx.integer("amount")).thenReturn(5);

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verify(sink).removeSouls(holder, gemId, 5);
        verifyNoMoreInteractions(sink); // exactly one debit, never two
    }

    @Test
    void noOpWhenNotInSoulMode() {
        Player holder = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.activeGem()).thenReturn(null); // no active gem
        when(ctx.actor()).thenReturn(holder);
        when(ctx.integer("amount")).thenReturn(5);

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }

    @Test
    void noOpOnNonPositiveAmount() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.activeGem()).thenReturn(UUID.randomUUID());
        when(ctx.actor()).thenReturn(mock(Player.class));
        when(ctx.integer("amount")).thenReturn(0);

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
