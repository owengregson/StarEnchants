package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.BlockFieldProfile;
import engine.sink.Sink;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import testfx.FakeEffectCtx;

/**
 * FALLING_BLOCK's per-target fan-out: ONE field intent per aimed target, carrying that target's own identity,
 * its palette and its authored profile. The grid's expansion itself lives in the sink (all of it is random),
 * and its geometry is pinned in {@code FieldProfileTest}.
 */
class FallingBlockEffectTest {

    /** Today's plain grid: every profile knob inert, no palette, no percent damage, no counterplay material. */
    private static FakeEffectCtx plainGrid() {
        return FakeEffectCtx.create()
                .with("material", 5).with("radius", 1).with("height", 4).with("ttl", 40).with("carry", 0.0)
                .with("layers-min", 1).with("layers-max", 1)
                .with("layer-step-min", 0).with("layer-step-max", 0)
                .with("density", 100.0)
                .with("damage-percent", 0.0).with("health-cap", 0.0)
                .with("rehit-max", 0).with("rehit-window", 200);
    }

    @Test
    void anUnprofiledFieldIsStillExactlyTodaysGrid() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        UUID target = UUID.randomUUID();
        when(who.getUniqueId()).thenReturn(target);
        when(who.getLocation()).thenReturn(new Location(world, 10, 64, 20)); // real Location (getBlockX/Y/Z are final)
        Sink sink = mock(Sink.class);
        ArgumentCaptor<BlockFieldProfile> profile = ArgumentCaptor.forClass(BlockFieldProfile.class);

        new FallingBlockEffect().run(plainGrid().targets("who", who), sink);

        // One intent, the single-material palette, the aimed target's own UUID, no owner (no actor), no carry.
        // The trailing -1: content authoring no `group:` stays UNSCOPED, so the landing fires the owner's whole
        // IMPACT roster exactly as it did before the scoping existed.
        verify(sink).fallingBlockField(any(Location.class), eq(List.of(5)), profile.capture(),
                eq(40), isNull(), eq(target), eq(0.0), eq(-1));
        assertEquals(1, profile.getValue().radius(), "the 3x3 grid the content authored");
        assertArrayEquals(new int[] {4}, profile.getValue().layerYOffsets(new Random()), "one layer, at height");
        assertTrue(profile.getValue().spawns(new Random()), "every position rains");
    }

    @Test
    void twoTargetsEachGetTheirOwnFieldCarryingTheirOwnUuid() {
        World world = mock(World.class);
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        when(a.getUniqueId()).thenReturn(idA);
        when(b.getUniqueId()).thenReturn(idB);
        when(a.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(b.getLocation()).thenReturn(new Location(world, 30, 64, 30));
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(plainGrid().targets("who", a, b), sink);

        verify(sink).fallingBlockField(any(), any(), any(), anyInt(), isNull(), eq(idA), anyDouble(), anyInt());
        verify(sink).fallingBlockField(any(), any(), any(), anyInt(), isNull(), eq(idB), anyDouble(), anyInt());
    }

    @Test
    void thePaletteAndCounterplayMaterialReachTheFieldInAuthoredOrder() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        when(who.getUniqueId()).thenReturn(UUID.randomUUID());
        when(who.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        Sink sink = mock(Sink.class);
        ArgumentCaptor<BlockFieldProfile> profile = ArgumentCaptor.forClass(BlockFieldProfile.class);
        // material3 is authored while material2 is not, so a palette built by position rather than by presence
        // would silently drop it (or pad the list with a phantom entry).
        FakeEffectCtx ctx = plainGrid().with("material3", 9).with("kill-material", 77).targets("who", who);

        new FallingBlockEffect().run(ctx, sink);

        verify(sink).fallingBlockField(any(), eq(List.of(5, 9)), profile.capture(),
                anyInt(), isNull(), any(), anyDouble(), anyInt());
        assertEquals(77, profile.getValue().killMaterialId());
    }

    @Test
    @SuppressWarnings("deprecation") // getMaxHealth(): the accessor the effect itself reads, stubbed here
    void percentOfCappedMaxHealthIsAddedOnTopOfTheFlatCarry() {
        World world = mock(World.class);
        LivingEntity who = mock(LivingEntity.class);
        UUID target = UUID.randomUUID();
        when(who.getUniqueId()).thenReturn(target);
        when(who.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(who.getMaxHealth()).thenReturn(60.0); // well above the cap, so an ignored cap shows up as 9.0
        Sink sink = mock(Sink.class);
        FakeEffectCtx ctx = plainGrid()
                .with("carry", 2.0).with("damage-percent", 15.0).with("health-cap", 44.0)
                .targets("who", who);

        new FallingBlockEffect().run(ctx, sink);

        verify(sink).fallingBlockField(any(), any(), any(), anyInt(), isNull(), eq(target),
                eq(2.0 + 15.0 / 100.0 * 44.0), anyInt());
    }

    @Test
    void skipsATargetWhoseLocationReadFaults() {
        LivingEntity remote = mock(LivingEntity.class); // @Attacker on a DEFENSE trigger can be a cross-region shooter
        when(remote.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        Sink sink = mock(Sink.class);

        new FallingBlockEffect().run(plainGrid().targets("who", remote), sink); // swallowed, not propagated

        verifyNoInteractions(sink);
    }
}
