package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class BreakBlockEffectTest {

    @Test
    void emitsBreakBlockWithDropsFlagPerTarget() {
        Location a = mock(Location.class);
        Location b = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targetLocations("at")).thenReturn(List.of(a, b));
        when(ctx.bool("drops")).thenReturn(false);

        Sink sink = mock(Sink.class);
        new BreakBlockEffect().run(ctx, sink);

        verify(sink).breakBlock(a, false);
        verify(sink).breakBlock(b, false);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noTargetLocationsIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targetLocations("at")).thenReturn(List.of());

        Sink sink = mock(Sink.class);
        new BreakBlockEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
