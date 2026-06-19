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

/**
 * Mock-host test for the canonical {@code MODIFY_MONEY} (which replaced GIVE_MONEY/TAKE_MONEY): give deposits
 * to each target, take withdraws, and transfer withdraws from each target and deposits the total into the
 * activator (steal). Non-players are skipped.
 */
class MoneyEffectTest {

    @Test
    void giveModeDepositsToTargets() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(100.0);
        when(ctx.str("mode")).thenReturn("give");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new MoneyEffect().run(ctx, sink);

        verify(sink).giveMoney(p, 100.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void takeModeWithdrawsFromTargets() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(50.0);
        when(ctx.str("mode")).thenReturn("take");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new MoneyEffect().run(ctx, sink);

        verify(sink).takeMoney(p, 50.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void transferModeTakesFromTargetAndGivesToActor() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // skipped, not a player
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(25.0);
        when(ctx.str("mode")).thenReturn("transfer");
        when(ctx.targets("who")).thenReturn(List.of(victim, mob));
        when(ctx.actor()).thenReturn(actor);

        Sink sink = mock(Sink.class);
        new MoneyEffect().run(ctx, sink);

        verify(sink).takeMoney(victim, 25.0);
        verify(sink).giveMoney(actor, 25.0); // the activator gains the taken total
        verifyNoMoreInteractions(sink);
    }

    @Test
    void stealPercentTransfersAFractionToTheActor() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // skipped, not a player
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(50.0); // a percentage
        when(ctx.str("mode")).thenReturn("steal_percent");
        when(ctx.targets("who")).thenReturn(List.of(victim, mob));
        when(ctx.actor()).thenReturn(actor);

        Sink sink = mock(Sink.class);
        new MoneyEffect().run(ctx, sink);

        verify(sink).stealMoneyPercent(victim, actor, 0.5); // 50% of the victim's balance → the actor
        verifyNoMoreInteractions(sink);
    }
}
