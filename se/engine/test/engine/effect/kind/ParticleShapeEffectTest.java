package engine.effect.kind;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import testfx.FakeEffectCtx;

/** Geometry of the shaped-particle effects — point counts, the per-point colour draw delegated to the Sink. */
class ParticleShapeEffectTest {

    // A REAL Location (its getWorld/getX/getY/getZ are final — unmockable); only the World is a mock.
    private static Location locAt(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    @Test
    void ringEmitsExactlyCountColouredMotes() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getLocation()).thenReturn(locAt(world, 0, 64, 0));
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("particle", 7).with("r", 255).with("g", 255).with("b", 255)
                .with("size", 1.0).with("radius", 3.0).with("count", 8).with("height", 1.0)
                .targets("who", who);
        Sink sink = mock(Sink.class);

        new ParticleRingEffect().run(ctx, sink);

        // One dust intent per ring point, tinted by the authored colour (white here).
        verify(sink, times(8)).dust(any(Location.class), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat(), anyInt());
    }

    @Test
    void lineEmitsDensityProportionalPointsFromTargetToActor() {
        World world = mock(World.class);
        Player actor = mock(Player.class);
        LivingEntity target = mock(LivingEntity.class);
        when(target.getLocation()).thenReturn(locAt(world, 5, 64, 0)); // 5 blocks away on x
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("particle", 7).with("r", 170).with("g", 0).with("b", 0)
                .with("size", 1.0).with("density", 2.0).with("height", 1.0)
                .actor(actor).actorOrigin(locAt(world, 0, 64, 0)).targets("who", target);
        Sink sink = mock(Sink.class);

        new ParticleLineEffect().run(ctx, sink);

        // dist 5 * density 2 = 10 segments → 11 inclusive points (s = 0..steps). The actor anchor is the
        // ADR-0043 snapshot, never a live read on the (possibly remote) actor.
        verify(sink, times(11)).dust(any(Location.class), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat(), anyInt());
        verify(actor, never()).getLocation();
    }

    @Test
    void ringOnTheActorReadsOnlyTheOriginSnapshot() {
        World world = mock(World.class);
        Player self = mock(Player.class); // @Self resolves the actor into the "who" slot
        FakeEffectCtx ctx = ringCtx()
                .actor(self).actorOrigin(locAt(world, 0, 64, 0)).targets("who", self);
        Sink sink = mock(Sink.class);

        new ParticleRingEffect().run(ctx, sink);

        verify(sink, times(8)).dust(any(Location.class), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat(), anyInt());
        verify(self, never()).getLocation(); // the snapshot is the sole actor read
    }

    @Test
    void ringOnTheActorWithNoOriginFailsClosed() {
        Player self = mock(Player.class);
        FakeEffectCtx ctx = ringCtx().actor(self).targets("who", self); // no actorOrigin → uncapturable
        Sink sink = mock(Sink.class);

        new ParticleRingEffect().run(ctx, sink);

        verifyNoInteractions(sink);
    }

    @Test
    void ringSkipsANonActorTargetWhoseLocationReadFaults() {
        Player actor = mock(Player.class);
        LivingEntity remote = mock(LivingEntity.class); // a cross-region resolved target
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        FakeEffectCtx ctx = ringCtx().actor(actor).targets("who", remote);
        Sink sink = mock(Sink.class);

        new ParticleRingEffect().run(ctx, sink); // guarded: the thrown read is swallowed, not propagated

        verifyNoInteractions(sink);
    }

    /** A ring ctx with all args set to fixed values (count 8), missing only the actor/target/origin wiring. */
    private static FakeEffectCtx ringCtx() {
        return FakeEffectCtx.create()
                .with("particle", 7).with("r", 255).with("g", 255).with("b", 255)
                .with("size", 1.0).with("radius", 3.0).with("count", 8).with("height", 1.0);
    }
}
