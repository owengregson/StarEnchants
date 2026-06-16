package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import engine.effect.EffectCtx;
import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mock-host test (docs/architecture.md §1.3): a mocked {@link EffectCtx} feeds typed
 * args + resolved targets, and a mocked {@link Sink} records the emitted intents — so
 * the effect's behavior is verified with no server.
 */
class SpawnTntEffectTest {

    @Test
    void emitsOneSpawnTntIntentPerResolvedTarget() {
        Location la = mock(Location.class);
        Location lb = mock(Location.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        when(a.getLocation()).thenReturn(la);
        when(b.getLocation()).thenReturn(lb);

        EffectCtx ctx = mock(EffectCtx.class);
        when(ctx.integer("count")).thenReturn(3);
        when(ctx.targets("who")).thenReturn(List.of(a, b));

        Sink sink = mock(Sink.class);
        new SpawnTntEffect().run(ctx, sink);

        verify(sink).spawnTnt(la, 3);
        verify(sink).spawnTnt(lb, 3);
        verifyNoMoreInteractions(sink);
    }
}
