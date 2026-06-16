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

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds typed
 * args + resolved targets, and a mocked {@link Sink} records the emitted intents — so
 * the effect's behavior is verified with no server. This is the template every effect
 * kind's unit test follows.
 */
class HealthEffectTest {

    @Test
    void emitsOneAddMaxHealthIntentPerResolvedTarget() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("amount")).thenReturn(4.0);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new HealthEffect().run(ctx, sink);

        verify(sink).addMaxHealth(a, 4.0);
        verify(sink).addMaxHealth(b, 4.0);
        verifyNoMoreInteractions(sink);
    }
}
