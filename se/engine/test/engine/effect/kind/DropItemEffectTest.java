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

/** Mock-host DROP_ITEM test: one dropItem at the location, no-op when none resolved. */
class DropItemEffectTest {

    @Test
    void emitsDropItemAtLocation() {
        Location loc = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("material")).thenReturn(11);
        when(ctx.integer("count")).thenReturn(3);

        Sink sink = mock(Sink.class);
        new DropItemEffect().run(ctx, sink);

        verify(sink).dropItem(loc, 11, 3);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noLocationIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new DropItemEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
