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
 * {@code blinkForward} intent carrying the origin's YAW-ONLY horizontal direction and the authored
 * args — pitch is stripped because a grounded player looking down would otherwise ray straight into
 * the floor cell and zero every blink. The standability walk itself lives in the sink
 * ({@code BlinkForwardTest}). No origin → no emit.
 */
class BlinkEffectTest {

    @Test
    void emitsBlinkForwardWithHorizontalDirectionAndDistinctArgs() {
        Player actor = mock(Player.class);
        // Distinct yaw + a real downward pitch: the emitted direction must match the pitch-0 twin of the
        // origin (yaw kept, pitch stripped); distinct r/g/b/size/count guard a mis-ordered argument list.
        Location origin = new Location(null, 1, 2, 3, 40f, -15f);
        Vector expectedDir = new Location(null, 1, 2, 3, 40f, 0f).getDirection();
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
    void steepDownwardLookStillBlinksLevel() {
        Player actor = mock(Player.class);
        Location origin = new Location(null, 0, 64, 0, 0f, 89f); // pitch 89 → looking almost straight down
        FakeEffectCtx ctx = FakeEffectCtx.create().actor(actor).actorOrigin(origin)
                .with("distance", 4.0).with("particle", 1)
                .with("r", 1).with("g", 2).with("b", 3)
                .with("size", 1.0).with("count", 1);
        Sink sink = mock(Sink.class);

        new BlinkEffect().run(ctx, sink);

        Vector expectedDir = new Location(null, 0, 64, 0, 0f, 0f).getDirection(); // unit +Z, no y component
        verify(sink).blinkForward(actor, origin, expectedDir, 4.0, 1, 1, 2, 3, 1.0f, 1);
    }

    @Test
    void noActorOriginEmitsNothing() {
        FakeEffectCtx ctx = FakeEffectCtx.create().actor(mock(Player.class)); // actorOrigin left null (ADR-0043)
        Sink sink = mock(Sink.class);

        new BlinkEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }
}
