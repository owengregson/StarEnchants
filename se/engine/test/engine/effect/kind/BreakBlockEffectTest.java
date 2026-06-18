package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** Mock-host test for {@code BREAK_BLOCK}: emits one breakBlock at the location, no-op without one. */
class BreakBlockEffectTest {

    @Test
    void emitsBreakBlockWithDropsFlag() {
        Location loc = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.bool("drops")).thenReturn(false);

        Sink sink = mock(Sink.class);
        new BreakBlockEffect().run(ctx, sink);

        verify(sink).breakBlock(loc, false);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noLocationIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new BreakBlockEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
