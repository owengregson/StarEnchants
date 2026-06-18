package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for the canonical {@code VELOCITY} (which replaced the deleted THROW/LAUNCH/KNOCKBACK):
 * {@code mode=add} emits a launch per target; {@code mode=away} emits a knockback per target from the
 * actor's location.
 */
class VelocityEffectTest {

    @Test
    void addModeEmitsLaunchPerTarget() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("mode")).thenReturn("add");
        when(ctx.dbl("x")).thenReturn(0.0);
        when(ctx.dbl("y")).thenReturn(1.2);
        when(ctx.dbl("z")).thenReturn(0.0);
        when(ctx.dbl("strength")).thenReturn(0.0);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new VelocityEffect().run(ctx, sink);

        verify(sink).launch(a, 0.0, 1.2, 0.0);
        verify(sink).launch(b, 0.0, 1.2, 0.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void awayModeEmitsKnockbackFromActor() {
        LivingEntity a = mock(LivingEntity.class);
        Player actor = mock(Player.class);
        Location loc = mock(Location.class);
        when(actor.getLocation()).thenReturn(loc);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.str("mode")).thenReturn("away");
        when(ctx.dbl("x")).thenReturn(0.0);
        when(ctx.dbl("y")).thenReturn(0.0);
        when(ctx.dbl("z")).thenReturn(0.0);
        when(ctx.dbl("strength")).thenReturn(1.5);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.targets("who")).thenReturn(List.of(a));

        Sink sink = mock(Sink.class);
        new VelocityEffect().run(ctx, sink);

        verify(sink).knockback(a, loc, 1.5);
        verifyNoMoreInteractions(sink);
    }
}
