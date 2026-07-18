package engine.effect.kind;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/**
 * BLINK's emit (ADR-0071): the kind reads the ADR-0043 actor-origin snapshot and emits ONE
 * {@code blinkForward} intent carrying the origin's full 3D look direction and the authored args —
 * the standability walk itself lives in the sink ({@code BlinkForwardTest}). No origin → no emit.
 */
class BlinkEffectTest {

    @Test
    void emitsBlinkForwardWithOriginDirectionAndDistinctArgs() {
        Player actor = mock(Player.class);
        // Distinct yaw/pitch so a transposed direction would diverge; distinct r/g/b/size/count guard a
        // mis-ordered argument list.
        Location origin = new Location(null, 1, 2, 3, 40f, -15f);
        Vector expectedDir = origin.getDirection();
        FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor).actorOrigin(origin)
                .with("distance", 3.5).with("particle", 7)
                .with("r", 11).with("g", 22).with("b", 33)
                .with("size", 1.5).with("count", 9);
        Sink sink = mock(Sink.class);

        new BlinkEffect().run(ctx, sink);

        verify(sink).blinkForward(actor, origin, expectedDir, 3.5, 7, 11, 22, 33, 1.5f, 9);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void noActorOriginEmitsNothing() {
        FakeEffectCtx ctx = FakeEffectCtx.create().actor(mock(Player.class)); // actorOrigin left null (ADR-0043)
        Sink sink = mock(Sink.class);

        new BlinkEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
