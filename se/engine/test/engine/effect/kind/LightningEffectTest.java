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

class LightningEffectTest {

    @Test
    void emitsOneLightningAndDamageIntentPerResolvedTarget() {
        LivingEntity target = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("damage")).thenReturn(6.0);
        when(ctx.targets("who")).thenReturn(List.of(target));

        Sink sink = mock(Sink.class);
        new LightningEffect().run(ctx, sink);

        verify(sink).lightningAndDamage(target, 6.0);
        verifyNoMoreInteractions(sink);
    }
}
