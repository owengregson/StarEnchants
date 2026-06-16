package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds typed
 * args + resolved targets, and a mocked {@link Sink} records the emitted intents — so
 * the effect's behavior is verified with no server. Only player targets receive the
 * {@code giveExp} intent; non-player living entities are skipped.
 */
class GiveExpEffectTest {

    @Test
    void emitsOneGiveExpIntentPerResolvedPlayerTarget() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(50);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new GiveExpEffect().run(ctx, sink);

        verify(sink).giveExp(a, 50);
        verify(sink).giveExp(b, 50);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void skipsNonPlayerLivingTargets() {
        Player player = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("amount")).thenReturn(50);
        when(ctx.targets("who")).thenReturn(List.of(player, mob));

        Sink sink = mock(Sink.class);
        new GiveExpEffect().run(ctx, sink);

        verify(sink).giveExp(player, 50);
        verifyNoMoreInteractions(sink);
    }
}
