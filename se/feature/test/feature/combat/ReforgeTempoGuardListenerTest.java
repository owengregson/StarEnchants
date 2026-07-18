package feature.combat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.EngineDamage;
import engine.stores.HitTempoStore;
import java.util.UUID;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

/**
 * The LOWEST third-party fairness gate for Quickening Fang (ADR-0071): a hit on a victim carrying a live
 * stolen interval is cancelled UNLESS the attacker holds their own live interval — third parties see the
 * i-frames the tempo write erased, while concurrent Quickening holders each keep their cadence. A projectile
 * resolves to its shooter; an engine-damage frame is exempt.
 */
class ReforgeTempoGuardListenerTest {

    private final HitTempoStore hitTempo = new HitTempoStore();
    private final long[] clock = {0};
    private final ReforgeTempoGuardListener listener = new ReforgeTempoGuardListener(hitTempo, () -> clock[0]);

    private final UUID victimId = UUID.randomUUID();

    private static Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        return p;
    }

    private EntityDamageByEntityEvent hit(Entity damager) {
        Player victim = player(victimId);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        return event;
    }

    @Test
    void cancelsThirdPartyInsideStolenWindow() {
        hitTempo.stampStolen(victimId, UUID.randomUUID(), 10L); // a holder's interval, live until tick 10
        clock[0] = 5;
        EntityDamageByEntityEvent event = hit(player(UUID.randomUUID())); // a third party

        listener.onDamage(event);

        verifyCancelled(event);
    }

    @Test
    void passesHolder() {
        UUID holder = UUID.randomUUID();
        hitTempo.stampStolen(victimId, holder, 10L);
        clock[0] = 5;
        EntityDamageByEntityEvent event = hit(player(holder)); // the holder whose write opened the window

        listener.onDamage(event);

        verifyNotCancelled(event);
    }

    @Test
    void passesSecondHolderWithLiveStamp() {
        UUID holderOne = UUID.randomUUID();
        UUID holderTwo = UUID.randomUUID();
        hitTempo.stampStolen(victimId, holderOne, 10L);
        hitTempo.stampStolen(victimId, holderTwo, 10L); // two concurrent Quickening holders
        clock[0] = 5;
        EntityDamageByEntityEvent event = hit(player(holderTwo));

        listener.onDamage(event);

        verifyNotCancelled(event); // per-holder keying: the second holder keeps its own cadence
    }

    @Test
    void passesAfterExpiry() {
        hitTempo.stampStolen(victimId, UUID.randomUUID(), 10L);
        clock[0] = 10; // the interval has lapsed
        EntityDamageByEntityEvent event = hit(player(UUID.randomUUID()));

        listener.onDamage(event);

        verifyNotCancelled(event);
    }

    @Test
    void resolvesProjectileShooterAsAttacker() {
        UUID holder = UUID.randomUUID();
        hitTempo.stampStolen(victimId, holder, 10L);
        clock[0] = 5;
        Player shooter = player(holder);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter); // the holder shooting a bow keeps their cadence
        EntityDamageByEntityEvent event = hit(arrow);

        listener.onDamage(event);

        verifyNotCancelled(event);
    }

    @Test
    void skipsEngineDamageFrames() {
        hitTempo.stampStolen(victimId, UUID.randomUUID(), 10L);
        clock[0] = 5;
        EntityDamageByEntityEvent event = hit(player(UUID.randomUUID())); // would otherwise be gated

        EngineDamage.frame(() -> listener.onDamage(event));

        verifyNotCancelled(event); // engine DoT/reflect ticks obey the park economy, never this gate
    }

    @Test
    void unmarkedVictimPassesEveryone() {
        clock[0] = 5;
        EntityDamageByEntityEvent event = hit(player(UUID.randomUUID())); // no stolen interval on the victim

        listener.onDamage(event);

        verifyNotCancelled(event);
    }

    private static void verifyCancelled(EntityDamageByEntityEvent event) {
        verify(event).setCancelled(true);
    }

    private static void verifyNotCancelled(EntityDamageByEntityEvent event) {
        verify(event, never()).setCancelled(true);
    }
}
