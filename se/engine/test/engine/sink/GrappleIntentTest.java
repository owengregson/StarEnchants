package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.SchedulerBackend;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * The GRAPPLE sink intent (ADR-0071): entity mode draws the eye→victim line NOW and, after the priced flight
 * delay, teleport-reels the victim (velocity zeroed) with a brief Slowness and no damage; terrain mode draws
 * the line and launches the actor with the computed zip velocity after the delay. The line's mote fan-out is
 * {@code round(dist × density) + 1}. GrappleService (feature) picks the mode and geometry; this pins only the
 * mechanism the sink executes.
 */
class GrappleIntentTest {

    private final RecordingSchedulerBackend backend = new RecordingSchedulerBackend();
    private SchedulerBackend previous;

    @BeforeEach
    void setUp() {
        previous = Scheduling.isInitialized() ? Scheduling.backend() : null;
        Scheduling.install(backend);
    }

    @AfterEach
    void tearDown() {
        if (previous != null) {
            Scheduling.install(previous);
        }
    }

    private static RecordingSink sink() {
        return new RecordingSink(Envs.sink().build());
    }

    private static Player actor() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isValid()).thenReturn(true);
        return p;
    }

    private static LivingEntity victimAt(Location loc, boolean valid) {
        LivingEntity v = mock(LivingEntity.class);
        when(v.getUniqueId()).thenReturn(UUID.randomUUID());
        when(v.isValid()).thenReturn(valid);
        when(v.getLocation()).thenReturn(loc);
        when(v.getVelocity()).thenReturn(new Vector(0, 0, 0));
        return v;
    }

    @Test
    void reelTeleportsZeroesAndSlows() {
        RecordingSink sink = sink();
        sink.slowType = mock(PotionEffectType.class);
        Location eye = new Location(null, 0, 64, 0);
        Location reelTo = new Location(null, 2, 64, 0);
        LivingEntity victim = victimAt(new Location(null, 5, 64, 0), true);

        sink.grapple(actor(), eye, victim, reelTo, null, 3, 1, 1, 60, null, 1, 200, 220, 255, 1f, 3);
        sink.flush();

        assertEquals(1, backend.delayed.size());
        assertEquals(3L, backend.delayed.get(0).delayTicks()); // reeled after the priced flight
        backend.runDelayed();

        assertEquals(1, sink.teleports.size());
        assertEquals(reelTo, sink.teleports.get(0));
        verify(victim).setVelocity(new Vector(0, 0, 0));       // velocity zeroed
        verify(victim).addPotionEffect(any(PotionEffect.class)); // brief Slowness, no damage
    }

    @Test
    void reelSkippedWhenVictimInvalid() {
        RecordingSink sink = sink();
        sink.slowType = mock(PotionEffectType.class);
        LivingEntity victim = victimAt(new Location(null, 5, 64, 0), false);

        sink.grapple(actor(), new Location(null, 0, 64, 0), victim, new Location(null, 2, 64, 0),
                null, 3, 1, 1, 60, null, 1, 200, 220, 255, 1f, 3);
        sink.flush();
        backend.runDelayed();

        assertTrue(sink.teleports.isEmpty());               // a dead victim is not reeled
        verify(victim, never()).setVelocity(any(Vector.class));
        verify(victim, never()).addPotionEffect(any(PotionEffect.class));
    }

    @Test
    void zipLaunchesAfterFlight() {
        RecordingSink sink = sink();
        Player actor = actor();
        Vector zip = new Vector(0.8, 0.3, 0.0);
        Location hook = new Location(null, 6, 64, 0);

        sink.grapple(actor, new Location(null, 0, 64, 0), null, null, hook, 4, 0, 0, 0, zip, 1, 200, 220, 255, 1f, 3);
        sink.flush();

        assertEquals(1, backend.delayed.size());
        assertEquals(4L, backend.delayed.get(0).delayTicks());
        backend.runDelayed();

        verify(actor).setVelocity(zip); // zipped exactly once, along the computed vector
    }

    @Test
    void lineIsDrawnToTheVictim() {
        // dist eye→victim = 5; round(5 × density) + 1 motes, hand-checked at two densities.
        assertEquals(16, motesForDensity(3)); // round(15) + 1
        assertEquals(11, motesForDensity(2)); // round(10) + 1
    }

    private int motesForDensity(double density) {
        RecordingSink sink = sink();
        LivingEntity victim = victimAt(new Location(null, 5, 64, 0), true);
        sink.grapple(actor(), new Location(null, 0, 64, 0), victim, new Location(null, 2, 64, 0),
                null, 3, 1, 1, 60, null, 1, 200, 220, 255, 1f, density);
        sink.flush(); // the line draws at flush, before the delayed reel
        return sink.dust.size();
    }
}
