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
 * Mock-host test (docs/architecture.md §1.3): the {@code particle} arg arrives already lowered to an
 * interned id, so the effect reads it via {@code ctx.integer} and the Sink intent carries that int.
 */
class ParticleEffectTest {

    @Test
    void emitsOneParticleIntentAtTheActivationLocation() {
        Location loc = mock(Location.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("particle")).thenReturn(9);
        when(ctx.integer("count")).thenReturn(20);

        Sink sink = mock(Sink.class);
        new ParticleEffect().run(ctx, sink);

        verify(sink).particle(loc, 9, 20);
        verifyNoMoreInteractions(sink);
    }
}
