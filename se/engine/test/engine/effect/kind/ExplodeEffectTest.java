package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mock-host effect test (docs/architecture.md §1.3): mocked EffectCtx in, Sink intents verified. */
class ExplodeEffectTest {

    @Test
    void emitsOneExplodeIntentPerResolvedTarget() {
        Location loc = mock(Location.class);
        LivingEntity target = mock(LivingEntity.class);
        when(target.getLocation()).thenReturn(loc);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.dbl("power")).thenReturn(4.0);
        when(ctx.bool("breakBlocks")).thenReturn(false);
        when(ctx.targets("who")).thenReturn(List.of(target));

        Sink sink = mock(Sink.class);
        new ExplodeEffect().run(ctx, sink);

        verify(sink).explode(loc, 4.0, false);
        verifyNoMoreInteractions(sink);
    }
}
