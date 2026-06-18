package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test for {@code KNOCKBACK_CONTROL}: one {@code controlKnockback} per living target with the
 * configured multiplier + duration. The cancel/scale of the actual knockback event is integration-pinned
 * in the live suite (it is a separate Bukkit event).
 */
class KnockbackControlEffectTest {

    @Test
    void emitsControlKnockbackPerTarget() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("multiplier")).thenReturn(0.0);
        when(ctx.integer("duration")).thenReturn(2);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new KnockbackControlEffect().run(ctx, sink);

        verify(sink).controlKnockback(a, 0.0, 2);
        verify(sink).controlKnockback(b, 0.0, 2);
        verifyNoMoreInteractions(sink);
    }
}
