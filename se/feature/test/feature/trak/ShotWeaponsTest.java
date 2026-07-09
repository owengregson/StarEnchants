package feature.trak;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feature.compat.Hands;
import feature.compat.Projectiles;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins {@link ShotWeapons}: a bow/trident is remembered by the launched projectile's id and claimed once;
 * an unknown projectile (a thrown potion / another player's arrow) claims nothing.
 */
class ShotWeaponsTest {

    private final Projectiles projectiles = mock(Projectiles.class);
    private final Hands hands = mock(Hands.class);

    /** A stack whose clone() is itself, so the stored clone is identity-comparable in the test. */
    private static ItemStack selfCloning() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    @Test
    void bowShotIsStampedAndClaimedOnce() {
        ShotWeapons shots = new ShotWeapons(projectiles, hands);
        UUID arrowId = UUID.randomUUID();
        ItemStack bow = selfCloning();
        Projectile arrow = mock(Projectile.class);
        when(arrow.getUniqueId()).thenReturn(arrowId);
        Player shooter = mock(Player.class);
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        when(event.getEntity()).thenReturn(shooter);
        when(event.getBow()).thenReturn(bow);
        when(event.getProjectile()).thenReturn(arrow);

        shots.onShootBow(event);

        assertSame(bow, shots.claim(arrowId), "the launcher is returned to credit the fatal blow");
        assertNull(shots.claim(arrowId), "a second claim finds nothing (removed on claim)");
    }

    @Test
    void tridentLaunchStampsTheShootersMainHand() {
        ShotWeapons shots = new ShotWeapons(projectiles, hands);
        UUID tridentId = UUID.randomUUID();
        Projectile trident = mock(Projectile.class);
        when(trident.getUniqueId()).thenReturn(tridentId);
        Player shooter = mock(Player.class);
        when(trident.getShooter()).thenReturn(shooter);
        ItemStack main = selfCloning();
        when(hands.mainHand(shooter)).thenReturn(main);
        when(projectiles.isTrident(trident)).thenReturn(true);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(trident);

        shots.onLaunch(event);

        assertSame(main, shots.claim(tridentId));
    }

    @Test
    void nonTridentLaunchIsIgnored() {
        ShotWeapons shots = new ShotWeapons(projectiles, hands);
        UUID snowballId = UUID.randomUUID();
        Projectile snowball = mock(Projectile.class);
        when(snowball.getUniqueId()).thenReturn(snowballId);
        when(projectiles.isTrident(snowball)).thenReturn(false);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(snowball);

        shots.onLaunch(event);

        assertNull(shots.claim(snowballId), "a non-trident projectile carries no weapon");
    }

    @Test
    void unknownProjectileClaimsNothing() {
        ShotWeapons shots = new ShotWeapons(projectiles, hands);
        assertNull(shots.claim(UUID.randomUUID()));
    }
}
