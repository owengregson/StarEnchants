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

/** Mock-host test for {@code SET_BLOCK}: emits one blockChange at the location, no-op without one. */
class SetBlockEffectTest {

    @Test
    void emitsBlockChangeAtLocation() {
        Location loc = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("material")).thenReturn(7);

        Sink sink = mock(Sink.class);
        new SetBlockEffect().run(ctx, sink);

        verify(sink).blockChange(loc, 7);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noLocationIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new SetBlockEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
