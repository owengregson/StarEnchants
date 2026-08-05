package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.kind.Allies;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * R-QC17: the bell's wild-mob conversion picks whom the convert fights, and it was the last targeting path
 * blind to the alliance predicate — a party-mate standing in the ring was as good an "enemy" as anyone.
 */
class ConvertTargetAllyTest {

    private RecordingSink sink;
    private Player ringer;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        sink = new RecordingSink(Envs.sink().build());
        ringer = mock(Player.class);
        when(ringer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(ringer.getLocation()).thenReturn(mock(Location.class));
    }

    @AfterEach
    void tearDown() {
        Allies.resolver(null); // restore the no-alliance default so other tests are unaffected
        GuardianCasts.clearAll();
    }

    @Test
    void aWildConvertIsPointedPastAnAllyAtTheUnalliedPlayerInTheRing() {
        Player ally = player();
        Player foe = player();
        LivingEntity wild = mob();
        // The ally is FIRST in the ring, which is what the scan takes without the predicate.
        when(ringer.getNearbyEntities(9.0, 9.0, 9.0)).thenReturn(List.of(ally, wild, foe));
        Allies.resolver((a, b) -> a == ringer && b == ally);

        sink.convertSummons(ringer, 9.0, -1);

        assertEquals(foe, sink.guardTargets.get(wild), "the ally is walked past, not turned on");
    }

    @Test
    void aRingOfNothingButAlliesLeavesTheConvertWithNoTargetAtAll() {
        // The whole point: "no enemy in the ring" must mean the convert's aggro is dropped, not that it falls
        // back to the nearest body — which here is a team-mate.
        Player ally = player();
        LivingEntity wild = mob();
        when(ringer.getNearbyEntities(9.0, 9.0, 9.0)).thenReturn(List.of(ally, wild));
        Allies.resolver((a, b) -> a == ringer && b == ally);

        sink.convertSummons(ringer, 9.0, -1);

        assertNull(sink.guardTargets.get(wild));
    }

    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    private static LivingEntity mob() {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        return e;
    }
}
