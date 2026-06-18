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

/** Mock-host test for {@code INVINCIBLE}: one invincible intent per resolved target. */
class InvincibleEffectTest {

    @Test
    void emitsInvincibleForEachTarget() {
        LivingEntity target = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("ticks")).thenReturn(100);
        when(ctx.targets("who")).thenReturn(List.of(target));

        Sink sink = mock(Sink.class);
        new InvincibleEffect().run(ctx, sink);

        verify(sink).invincible(target, 100);
        verifyNoMoreInteractions(sink);
    }
}
