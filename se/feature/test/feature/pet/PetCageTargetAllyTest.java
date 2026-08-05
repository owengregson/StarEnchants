package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.kind.Allies;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * R-QC17: the cage's pre-cooldown scan has to answer the same question {@code @NearestPlayer} answers, or the
 * gesture stays alive for a target the selector will then refuse to hand it — burning the cooldown on nothing.
 */
class PetCageTargetAllyTest {

    private static final Location CENTER = mock(Location.class);

    @AfterEach
    void tearDown() {
        Allies.resolver(null); // restore the no-alliance default so other tests are unaffected
    }

    @Test
    void theScanWalksPastACloserAllyToTheNearestUnalliedPlayer() {
        Player user = user();
        Player ally = playerAt(1.0);
        Player foe = playerAt(64.0);
        when(user.getNearbyEntities(10.0, 10.0, 10.0)).thenReturn(List.of(ally, foe));
        Allies.resolver((a, b) -> a == user && b == ally);

        assertEquals(foe, PetService.nearestOtherPlayer(user, 10.0));
    }

    @Test
    void aRingOfNothingButAlliesAndMobsFindsNobody() {
        Player user = user();
        Player ally = playerAt(1.0);
        LivingEntity mob = mock(LivingEntity.class);
        when(user.getNearbyEntities(10.0, 10.0, 10.0)).thenReturn(List.of(ally, mob));
        Allies.resolver((a, b) -> a == user && b == ally);

        assertNull(PetService.nearestOtherPlayer(user, 10.0), "the refusal fires BEFORE the cooldown arms");
    }

    private static Player user() {
        Player p = mock(Player.class);
        when(p.getLocation()).thenReturn(CENTER);
        return p;
    }

    private static Player playerAt(double distSq) {
        Player p = mock(Player.class);
        Location at = mock(Location.class);
        when(at.distanceSquared(CENTER)).thenReturn(distSq);
        when(p.getLocation()).thenReturn(at);
        return p;
    }
}
