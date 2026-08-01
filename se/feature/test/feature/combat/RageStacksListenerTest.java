package feature.combat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

/**
 * The two-sided rage dispatch (ADR-0058): one melee/projectile event feeds BOTH the attacker's run ({@code onHit})
 * and the victim's break ({@code onHitTaken}). The defense side is the fix — a player who TAKES a hit has their
 * own run broken, so rage is a combo kept by not being hit. Self-inflicted damage feeds neither.
 */
class RageStacksListenerTest {

    private final RageStacksService service = mock(RageStacksService.class);
    private final RageStacksListener listener = new RageStacksListener(service);

    private EntityDamageByEntityEvent hit(Entity damager, LivingEntity victim) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        return event;
    }

    @Test
    void aPlayerVsPlayerHitBuildsTheAttackerAndBreaksTheVictim() {
        Player attacker = mock(Player.class);
        Player victim = mock(Player.class);

        listener.onDamage(hit(attacker, victim));

        verify(service).onHit(attacker, true);       // attack side: build/advance the attacker's run
        verify(service).onHitTaken(victim);    // defense side: the victim's own run is broken
    }

    @Test
    void aMobHittingAPlayerOnlyBreaksTheVictim() {
        LivingEntity mob = mock(LivingEntity.class); // not a Player → no attack-side run
        Player victim = mock(Player.class);

        listener.onDamage(hit(mob, victim));

        verify(service).onHitTaken(victim);    // getting hit by anything breaks your run
        verify(service, never()).onHit(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void aPlayerHittingAMobOnlyBuildsTheAttackerRun() {
        Player attacker = mock(Player.class);
        LivingEntity mob = mock(LivingEntity.class); // a non-player victim has no run of its own

        listener.onDamage(hit(attacker, mob));

        verify(service).onHit(attacker, false);
        verify(service, never()).onHitTaken(any());
    }

    @Test
    void selfInflictedDamageFeedsNeitherSide() {
        Player player = mock(Player.class);

        listener.onDamage(hit(player, player)); // damager == victim

        verifyNoInteractions(service);
    }

    @Test
    void aDispatchSkippedDuplicateAdvancesNoRunButStillBreaksTheVictim() {
        Player attacker = mock(Player.class);
        Player victim = mock(Player.class);
        EntityDamageByEntityEvent event = hit(attacker, victim);

        ReHitGuard.markSkipped(event); // the dispatch judged this a same-swing re-hit (§3.7)
        try {
            listener.onDamage(event);
        } finally {
            ReHitGuard.clearSkipped();
        }

        verify(service, never()).onHit(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(service).onHitTaken(victim);    // the break is idempotent and stays unguarded
    }
}
