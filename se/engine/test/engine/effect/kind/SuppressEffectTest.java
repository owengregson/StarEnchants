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

class SuppressEffectTest {

    @Test
    void suppressesEachPlayerTarget() {
        Player p = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("scope")).thenReturn(1); // GROUP
        when(ctx.integer("key")).thenReturn(7);    // interned cooldown-scope id
        when(ctx.integer("duration")).thenReturn(200);
        when(ctx.targets("who")).thenReturn(List.of(p, mob));

        Sink sink = mock(Sink.class);
        new SuppressEffect().run(ctx, sink);

        verify(sink).suppress(p, 1, 7, 200);
        verifyNoMoreInteractions(sink);
    }
}
