package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code REMOVE_SOULS}: debits the activator's active gem ({@code @Self} in soul mode),
 * drains a victim's own gem ({@code @Victim}), else no-op. Exactly ONE debit ever — guards the dupe risk.
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
        when(ctx.targets("who")).thenReturn(List.of(holder)); // @Self resolves to the activator

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verify(sink).removeSouls(holder, gemId, 5);
        verifyNoMoreInteractions(sink); // exactly one debit, never two
    }

    @Test
    void drainsAVictimsOwnGem() {
        Player holder = mock(Player.class);
        Player victim = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(holder);
        when(ctx.integer("amount")).thenReturn(300);
        when(ctx.targets("who")).thenReturn(List.<LivingEntity>of(victim)); // @Victim resolves to the foe

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verify(sink).removeSoulsFrom(victim, 300); // drains the victim's OWN gem (not the activator's)
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noOpWhenNotInSoulMode() {
        Player holder = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.activeGem()).thenReturn(null); // not in soul mode → debit must be suppressed
        when(ctx.actor()).thenReturn(holder);
        when(ctx.integer("amount")).thenReturn(5);
        when(ctx.targets("who")).thenReturn(List.of(holder));

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }

    @Test
    void noOpOnNonPositiveAmount() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(mock(Player.class));
        when(ctx.integer("amount")).thenReturn(0);

        Sink sink = mock(Sink.class);
        new RemoveSoulsEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
