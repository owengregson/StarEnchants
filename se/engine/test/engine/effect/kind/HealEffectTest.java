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

class HealEffectTest {

    @Test
    void emitsOneHealIntentPerResolvedTarget() {
        LivingEntity self = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(4.0);
        when(ctx.targets("who")).thenReturn(List.of(self));

        Sink sink = mock(Sink.class);
        new HealEffect().run(ctx, sink);

        verify(sink).heal(self, 4.0);
        verifyNoMoreInteractions(sink);
    }
}
