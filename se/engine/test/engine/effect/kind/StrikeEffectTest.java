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

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * activation location, and a mocked {@link Sink} records the location-targeted
 * lightning intent — verified with no server. With no location the effect is a no-op.
 */
class StrikeEffectTest {

    @Test
    void emitsLightningAtActivationLocation() {
        Location loc = mock(Location.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);

        Sink sink = mock(Sink.class);
        new StrikeEffect().run(ctx, sink);

        verify(sink).lightning(loc);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void isANoOpWhenTheActivationHasNoLocation() {
        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(null);

        Sink sink = mock(Sink.class);
        new StrikeEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
