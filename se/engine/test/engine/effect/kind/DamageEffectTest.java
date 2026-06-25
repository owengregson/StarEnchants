package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.entity.LivingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageEffectTest {

    @Test
    void emitsOneDamageIntentPerResolvedTarget() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(6.0);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new DamageEffect().run(ctx, sink);

        verify(sink).damage(a, 6.0);
        verify(sink).damage(b, 6.0);
        verifyNoMoreInteractions(sink);
    }
}
