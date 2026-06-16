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
 * Mock-host tests (docs/architecture.md §1.3) for the economy effects: a mocked {@link EffectCtx} feeds
 * the amount + resolved targets and a mocked {@link Sink} records the emitted money intents — so the
 * effects' behavior is verified with no server and no economy provider.
 */
class MoneyEffectsTest {

    @Test
    void giveMoneyDepositsToPlayerTargetsAndSkipsNonPlayers() {
        Player a = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(100.0);
        when(ctx.targets("who")).thenReturn(List.of(a, mob, b));

        Sink sink = mock(Sink.class);
        new GiveMoneyEffect().run(ctx, sink);

        verify(sink).giveMoney(a, 100.0);
        verify(sink).giveMoney(b, 100.0);
        verifyNoMoreInteractions(sink); // the mob target emits nothing
    }

    @Test
    void takeMoneyWithdrawsFromPlayerTargetsAndSkipsNonPlayers() {
        Player victim = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(50.0);
        when(ctx.targets("who")).thenReturn(List.of(victim, mob));

        Sink sink = mock(Sink.class);
        new TakeMoneyEffect().run(ctx, sink);

        verify(sink).takeMoney(victim, 50.0);
        verifyNoMoreInteractions(sink);
    }
}
