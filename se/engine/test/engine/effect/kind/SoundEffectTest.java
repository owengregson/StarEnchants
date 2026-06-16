package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * typed args + activation location, and a mocked {@link Sink} records the emitted
 * intent — so the effect's behavior is verified with no server. The {@code sound}
 * handle arrives already resolved to an interned id (§9).
 */
class SoundEffectTest {

    @Test
    void emitsSoundIntentAtActivationLocation() {
        Location loc = mock(Location.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("sound")).thenReturn(3);
        when(ctx.dbl("volume")).thenReturn(1.0);
        when(ctx.dbl("pitch")).thenReturn(1.0);

        Sink sink = mock(Sink.class);
        new SoundEffect().run(ctx, sink);

        verify(sink).sound(loc, 3, 1.0f, 1.0f);
        verifyNoMoreInteractions(sink);
    }
}
