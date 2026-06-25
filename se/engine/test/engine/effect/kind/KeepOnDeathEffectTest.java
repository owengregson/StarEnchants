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

class KeepOnDeathEffectTest {

    @Test
    void emitsKeepOnDeathForPlayerTargetsOnly() {
        Player player = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("duration")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(player, mob));

        Sink sink = mock(Sink.class);
        new KeepOnDeathEffect().run(ctx, sink);

        verify(sink).keepOnDeath(player, 200);
        verifyNoMoreInteractions(sink);
    }
}
