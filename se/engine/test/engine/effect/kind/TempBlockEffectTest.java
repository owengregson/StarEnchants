package engine.effect.kind;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** TEMP_BLOCK shape geometry — how many per-position tempBlock intents each shape emits. */
class TempBlockEffectTest {

    private static FakeEffectCtx ctx(String shape) {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        return FakeEffectCtx.create()
                .with("shape", shape).with("material", 7).with("ticks", 60)
                .with("radius", 1).with("height", 2).with("ahead", 0).with("dy", 0).with("airOnly", true)
                .targets("who", who);
    }

    @Test
    void pointEmitsOneBlock() {
        Sink sink = mock(Sink.class);
        new TempBlockEffect().run(ctx("POINT"), sink);
        verify(sink, times(1)).tempBlock(any(Location.class), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void footprintEmitsTheFullSquare() {
        Sink sink = mock(Sink.class);
        new TempBlockEffect().run(ctx("FOOTPRINT"), sink); // radius 1 → 3x3 = 9
        verify(sink, times(9)).tempBlock(any(Location.class), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    // A radius-0 FOOTPRINT is the snake: it routes to the trail intent (keyed by sourceDefId + the walker's
    // UUID) so the sink joins consecutive samples, NOT a point place that skips blocks and corner-touches.
    @Test
    void radiusZeroFootprintStampsTheTrail() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        UUID walker = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
        when(who.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        when(who.getUniqueId()).thenReturn(walker);
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("shape", "FOOTPRINT").with("material", 7).with("ticks", 40)
                .with("radius", 0).with("height", 1).with("ahead", 0).with("dy", -1).with("airOnly", false)
                .sourceDefId(88).targets("who", who);
        Sink sink = mock(Sink.class);

        new TempBlockEffect().run(ctx, sink);

        verify(sink, times(1)).tempBlockTrail(eq(88), eq(walker), any(Location.class), eq(7), eq(40));
        verify(sink, never()).tempBlock(any(Location.class), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void columnEmitsHeightBlocks() {
        Sink sink = mock(Sink.class);
        new TempBlockEffect().run(ctx("COLUMN"), sink); // height 2 → 2
        verify(sink, times(2)).tempBlock(any(Location.class), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void skipsATargetWhoseLocationReadFaults() {
        LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be a cross-region shooter
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("shape", "POINT").with("material", 7).with("ticks", 60)
                .with("radius", 0).with("height", 1).with("ahead", 0).with("dy", 0).with("airOnly", true)
                .targets("who", remote);
        Sink sink = mock(Sink.class);

        new TempBlockEffect().run(ctx, sink); // the thrown remote read is swallowed, not propagated

        verifyNoInteractions(sink);
    }
}
