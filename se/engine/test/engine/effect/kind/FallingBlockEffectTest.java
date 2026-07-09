package engine.effect.kind;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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

/** FALLING_BLOCK grid geometry — how many falling-block intents the (2r+1)² grid emits, plus the cross-region guard. */
class FallingBlockEffectTest {

    @Test
    void gridEmitsTheFullSquareEachColumnCarryingTheAimedTarget() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        UUID target = UUID.randomUUID();
        when(who.getUniqueId()).thenReturn(target);
        when(who.getLocation()).thenReturn(new Location(world, 10, 64, 20)); // real Location (getBlockX/Y/Z are final)
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("material", 5).with("radius", 1).with("height", 4).with("ttl", 40).with("carry", 0.0)
                .targets("who", who);
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(ctx, sink); // radius 1 → 3x3 = 9, owner null (no actor)

        // Every one of the 9 columns binds the SAME aimed-target UUID, so the landing hits only that entity.
        verify(sink, times(9)).fallingBlock(any(Location.class), anyInt(), anyInt(), isNull(), eq(target), anyDouble());
    }

    @Test
    void twoTargetsEachCarryTheirOwnUuid() {
        World world = mock(World.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        when(a.getUniqueId()).thenReturn(idA);
        when(b.getUniqueId()).thenReturn(idB);
        when(a.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(b.getLocation()).thenReturn(new Location(world, 30, 64, 30));
        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("material", 5).with("radius", 1).with("height", 4).with("ttl", 40).with("carry", 0.0)
                .targets("who", a, b);
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(ctx, sink); // two 3x3 grids, each carrying its own target UUID

        verify(sink, times(9)).fallingBlock(any(Location.class), anyInt(), anyInt(), isNull(), eq(idA), anyDouble());
        verify(sink, times(9)).fallingBlock(any(Location.class), anyInt(), anyInt(), isNull(), eq(idB), anyDouble());
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
