package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Non-players are skipped — only players have a hunger bar. */
class FoodEffectTest {

    @Test
    void giveModeFeedsPlayers() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(6);
        when(ctx.str("mode")).thenReturn("give");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new FoodEffect().run(ctx, sink);

        verify(sink).feed(p, 6);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void takeModeDrainsPlayers() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(4);
        when(ctx.str("mode")).thenReturn("take");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new FoodEffect().run(ctx, sink);

        verify(sink).takeFood(p, 4);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void nonPlayersAreSkipped() {
        LivingEntity mob = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(6);
        when(ctx.str("mode")).thenReturn("give");
        when(ctx.targets("who")).thenReturn(List.of(mob));

        Sink sink = mock(Sink.class);
        new FoodEffect().run(ctx, sink);

        verifyNoMoreInteractions(sink);
    }
}
