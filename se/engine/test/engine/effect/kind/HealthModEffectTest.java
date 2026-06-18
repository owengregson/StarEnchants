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
 * Mock-host test for the canonical {@code MODIFY_HEALTH} (which replaced HEAL): give heals each
 * target, take deals direct health damage, and transfer (lifesteal) damages each target and heals
 * the activator by the same total.
 */
class HealthModEffectTest {

    @Test
    void giveModeHealsTargets() {
        LivingEntity a = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(4.0);
        when(ctx.str("mode")).thenReturn("give");
        when(ctx.targets("who")).thenReturn(List.of(a));

        Sink sink = mock(Sink.class);
        new HealthModEffect().run(ctx, sink);

        verify(sink).heal(a, 4.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void takeModeDamagesTargets() {
        LivingEntity a = mock(LivingEntity.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(6.0);
        when(ctx.str("mode")).thenReturn("take");
        when(ctx.targets("who")).thenReturn(List.of(a));

        Sink sink = mock(Sink.class);
        new HealthModEffect().run(ctx, sink);

        verify(sink).damage(a, 6.0);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void transferModeDamagesTargetAndHealsActor() {
        LivingEntity victim = mock(LivingEntity.class);
        Player actor = mock(Player.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(5.0);
        when(ctx.str("mode")).thenReturn("transfer");
        when(ctx.targets("who")).thenReturn(List.of(victim));
        when(ctx.actor()).thenReturn(actor);

        Sink sink = mock(Sink.class);
        new HealthModEffect().run(ctx, sink);

        verify(sink).damage(victim, 5.0);
        verify(sink).heal(actor, 5.0); // lifesteal: the activator gains what was drained
        verifyNoMoreInteractions(sink);
    }
}
