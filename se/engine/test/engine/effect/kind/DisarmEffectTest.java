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

/** Mock-host effect test (docs/architecture.md §1.3): mocked EffectCtx in, Sink intents verified. */
class DisarmEffectTest {

    @Test
    void emitsOneDisarmIntentPerResolvedTarget() {
        LivingEntity victim = mock(LivingEntity.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targets("who")).thenReturn(List.of(victim));

        Sink sink = mock(Sink.class);
        new DisarmEffect().run(ctx, sink);

        verify(sink).disarm(victim);
        verifyNoMoreInteractions(sink);
    }
}
