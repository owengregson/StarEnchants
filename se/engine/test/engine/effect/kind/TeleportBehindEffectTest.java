package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.Sink;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import testfx.FakeEffectCtx;

/** TELEPORT_BEHIND geometry: the preferred spot is `distance` behind the reference's facing; fallback selection. */
class TeleportBehindEffectTest {

    @Test
    void blinksOneBlockBehindThePlusZFacingReferenceWithAnOnTopFallback() {
        World world = mock(World.class);
        Location refLoc = new Location(world, 10, 64, 20); // yaw 0, pitch 0 → faces +Z
        LivingEntity attacker = mock(LivingEntity.class);  // on a DEFENSE trigger the VICTIM slot IS the attacker
        when(attacker.getLocation()).thenReturn(refLoc);
        Player wearer = mock(Player.class);
        when(wearer.getEyeLocation()).thenReturn(new Location(world, 0, 65, 0));
        LivingEntity mover = mock(LivingEntity.class);

        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("of", "VICTIM").with("distance", 1.0).with("onFail", "ONTOP")
                .actor(wearer).victim(attacker).targets("who", mover);
        Sink sink = mock(Sink.class);

        new TeleportBehindEffect().run(ctx, sink);

        ArgumentCaptor<Location> preferred = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<Location> fallback = ArgumentCaptor.forClass(Location.class);
        verify(sink).teleportSafe(eq(mover), preferred.capture(), fallback.capture(), any());
        // 1 block behind a +Z-facing reference: z decreases by 1, x unchanged.
        assertEquals(10.0, preferred.getValue().getX(), 1.0e-9);
        assertEquals(19.0, preferred.getValue().getZ(), 1.0e-9);
        // ONTOP fallback is the reference's own spot.
        assertEquals(20.0, fallback.getValue().getZ(), 1.0e-9);
    }

    @Test
    void onFailNoneGivesANullFallback() {
        World world = mock(World.class);
        LivingEntity attacker = mock(LivingEntity.class);
        when(attacker.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        LivingEntity mover = mock(LivingEntity.class);

        FakeEffectCtx ctx = FakeEffectCtx.create()
                .with("of", "VICTIM").with("distance", 2.0).with("onFail", "NONE")
                .victim(attacker).targets("who", mover); // no actor → sightFrom falls back to the reference loc
        Sink sink = mock(Sink.class);

        new TeleportBehindEffect().run(ctx, sink);

        verify(sink).teleportSafe(eq(mover), any(Location.class), isNull(), any());
    }
}
