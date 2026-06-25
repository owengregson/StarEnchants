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

/** Mock-host effect test (docs/architecture.md §1.3): mocked EffectCtx in, Sink intents verified. */
class IgniteEffectTest {

    @Test
    void emitsOneIgniteIntentPerResolvedTarget() {
        LivingEntity target = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("duration")).thenReturn(60);
        when(ctx.targets("who")).thenReturn(List.of(target));

        Sink sink = mock(Sink.class);
        new IgniteEffect().run(ctx, sink);

        verify(sink).ignite(target, 60);
        verifyNoMoreInteractions(sink);
    }
}
