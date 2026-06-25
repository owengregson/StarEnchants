package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ProjectileEffectTest {

    @Test
    void emitsLaunchProjectileFromActor() {
        Player actor = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.integer("type")).thenReturn(6);
        when(ctx.integer("count")).thenReturn(3);
        when(ctx.dbl("speed")).thenReturn(1.5);

        Sink sink = mock(Sink.class);
        new ProjectileEffect().run(ctx, sink);

        verify(sink).launchProjectile(actor, 6, 3, 1.5);
        verifyNoMoreInteractions(sink);
    }
}
