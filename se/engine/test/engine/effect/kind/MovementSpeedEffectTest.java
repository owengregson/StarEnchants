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

class MovementSpeedEffectTest {

    @Test
    void emitsMovementSpeedForPlayerTargetsOnly() {
        Player player = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("speed")).thenReturn(0.4);
        when(ctx.integer("ticks")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(player, mob));

        Sink sink = mock(Sink.class);
        new MovementSpeedEffect().run(ctx, sink);

        verify(sink).movementSpeed(player, 0.4, 200);
        verifyNoMoreInteractions(sink);
    }
}
