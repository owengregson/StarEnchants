package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds the
 * resolved location and typed (handle + count) args, and a mocked {@link Sink} records
 * the emitted {@code spawn} intents — so the effect's behavior is verified with no
 * server. The handle {@code type} arrives already resolved to an interned id (§9).
 */
class SpawnEffectTest {

    @Test
    void emitsOneSpawnIntentPerCount() {
        Location loc = mock(Location.class);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.location()).thenReturn(loc);
        when(ctx.integer("type")).thenReturn(4);
        when(ctx.integer("count")).thenReturn(2);

        Sink sink = mock(Sink.class);
        new SpawnEffect().run(ctx, sink);

        verify(sink, times(2)).spawn(loc, 4);
        verifyNoMoreInteractions(sink);
    }
}
