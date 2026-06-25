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

class FireworkEffectKindTest {

    @Test
    void emitsFireworkAtLocation() {
        Location loc = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("power")).thenReturn(2);

        Sink sink = mock(Sink.class);
        new FireworkEffectKind().run(ctx, sink);

        verify(sink).firework(loc, 2);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noLocationIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new FireworkEffectKind().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
