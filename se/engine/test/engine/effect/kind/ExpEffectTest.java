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
 * Mock-host test for the canonical {@code MODIFY_EXP} (which replaced GIVE_EXP): give grants to each
 * target, take withdraws, and transfer withdraws from each target and grants the total to the
 * activator (steal). Non-players are skipped.
 */
class ExpEffectTest {

    @Test
    void giveModeGrantsToTargets() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(50);
        when(ctx.str("mode")).thenReturn("give");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new ExpEffect().run(ctx, sink);

        verify(sink).giveExp(p, 50);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void takeModeWithdrawsFromTargets() {
        Player p = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(20);
        when(ctx.str("mode")).thenReturn("take");
        when(ctx.targets("who")).thenReturn(List.of(p));

        Sink sink = mock(Sink.class);
        new ExpEffect().run(ctx, sink);

        verify(sink).takeExp(p, 20);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void transferModeTakesFromTargetAndGrantsToActor() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // skipped, not a player
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(25);
        when(ctx.str("mode")).thenReturn("transfer");
        when(ctx.targets("who")).thenReturn(List.of(victim, mob));
        when(ctx.actor()).thenReturn(actor);

        Sink sink = mock(Sink.class);
        new ExpEffect().run(ctx, sink);

        verify(sink).takeExp(victim, 25);
        verify(sink).giveExp(actor, 25); // the activator gains the taken total
        verifyNoMoreInteractions(sink);
    }
}
