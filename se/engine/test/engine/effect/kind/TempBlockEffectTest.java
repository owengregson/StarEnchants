package engine.effect.kind;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
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

    @Test
    void columnEmitsHeightBlocks() {
        Sink sink = mock(Sink.class);
        new TempBlockEffect().run(ctx("COLUMN"), sink); // height 2 → 2
        verify(sink, times(2)).tempBlock(any(Location.class), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
}
