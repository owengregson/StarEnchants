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

/** Mock-host test for {@code SET_BLOCK}: emits one blockChange per target location, no-op without any. */
class SetBlockEffectTest {

    @Test
    void emitsBlockChangeAtEachTargetLocation() {
        Location a = mock(Location.class);
        Location b = mock(Location.class);
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targetLocations("at")).thenReturn(List.of(a, b)); // e.g. an @Trench resolved to two blocks
        when(ctx.integer("material")).thenReturn(7);

        Sink sink = mock(Sink.class);
        new SetBlockEffect().run(ctx, sink);

        verify(sink).blockChange(a, 7);
        verify(sink).blockChange(b, 7);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noTargetLocationsIsNoOp() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.targetLocations("at")).thenReturn(List.of());

        Sink sink = mock(Sink.class);
        new SetBlockEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
