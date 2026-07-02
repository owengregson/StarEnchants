package engine.effect.kind;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** FALLING_BLOCK grid geometry — how many falling-block intents the (2r+1)² grid emits, plus the cross-region guard. */
class FallingBlockEffectTest {

    @Test
    void gridEmitsTheFullSquare() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(new Location(world, 10, 64, 20)); // real Location (getBlockX/Y/Z are final)
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("material", 5).with("radius", 1).with("height", 4).with("ttl", 40).with("carry", 0.0)
                .targets("who", who);
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(ctx, sink); // radius 1 → 3x3 = 9, owner null (no actor)

        verify(sink, times(9)).fallingBlock(any(Location.class), anyInt(), anyInt(), isNull(), anyDouble());
    }

    @Test
    void skipsATargetWhoseLocationReadFaults() {
        LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be a cross-region shooter
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("material", 5).with("radius", 1).with("height", 4).with("ttl", 40).with("carry", 0.0)
                .targets("who", remote);
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(ctx, sink); // the thrown remote read is swallowed, not propagated

        verifyNoInteractions(sink);
    }
}
