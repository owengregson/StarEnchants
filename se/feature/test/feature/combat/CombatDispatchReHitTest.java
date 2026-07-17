package feature.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import engine.run.AbilityExecutor;
import engine.run.ActorProbe;
import engine.sink.SinkFactory;
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import testfx.Envs;
import testfx.Snapshots;

/**
 * The §3.7 duplicate skip keys on HIT IDENTITY (the ReHitGuard stamp), not on the shared i-frame window
 * alone, and relays the skip to MONITOR consumers. The distinct-hit-inside-a-window paths (a second
 * attacker, an environmental window) are proven live in ReHitOnceSuite — a booted server is the only
 * honest oracle for vanilla's damage-the-difference event.
 */
class CombatDispatchReHitTest {

    @AfterEach
    void clearRelay() {
        ReHitGuard.clearSkipped();
    }

    @Test
    void aStampedSameAttackerReHitInsideTheWindowIsSkippedAndRelayed() {
        SinkFactory sinkFactory = mock(SinkFactory.class);
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(Snapshots.snapshot().build());
        CombatDispatch dispatch = new CombatDispatch(mock(AbilityExecutor.class), sinkFactory,
                mock(ActorProbe.class), content, mock(WornStateStore.class), 0, 1, -1, -1,
                p -> Optional.empty(), Envs.sink().build(), CombatDispatch.Caps.unlimited(),
                new ModernProjectiles());
        Player attacker = mock(Player.class);
        UUID attackerId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(attackerId);
        LivingEntity victim = mock(LivingEntity.class);
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getNoDamageTicks()).thenReturn(20);      // mid-window (> max/2)
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);

        dispatch.reHits().stamp(victimId, attackerId, 0L); // this attacker's landed hit opened the window

        dispatch.onDamage(event);

        verifyNoInteractions(sinkFactory); // no sink, no walks, no fold — the duplicate is dropped whole
        assertTrue(ReHitGuard.skipped(event), "the skip must be relayed to MONITOR consumers (rage)");
    }
}
