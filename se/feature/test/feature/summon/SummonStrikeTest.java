package feature.summon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.GuardianCasts;
import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import feature.trigger.TriggerDispatch;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The strike phase. Consume is the once-only guard — a courier summon must pay its owner's IMPACT exactly once
 * however many times the hit is delivered — and cancel is what keeps the summon's own melee from landing on
 * top of the authored payload.
 */
class SummonStrikeTest {

    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);
    private final SummonStrikeListener listener = new SummonStrikeListener(dispatch, () -> true);

    private final Player owner = mock(Player.class);
    private final UUID ownerId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        PetSummons.clearAll();
        GuardianCasts.clearAll();
    }

    private static SummonFlags strike(boolean consume, boolean cancel) {
        return SummonFlags.NONE.withPayload(SummonFlags.PHASE_STRIKE, 40, 4.0, 0.0, "ALL", 0, 0)
                .withStrike(consume, cancel);
    }

    /** Register {@code summon} as an armed strike courier owned by {@link #ownerId}. */
    private UUID arm(Entity summon, SummonFlags flags) {
        UUID id = UUID.randomUUID();
        when(summon.getUniqueId()).thenReturn(id);
        PetSummons.bind(id, flags);
        GuardianCasts.bind(id, ownerId);
        return id;
    }

    private static EntityDamageByEntityEvent hit(Entity summon, Entity victim, double damage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(summon);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(damage);
        return event;
    }

    /** The owner is resolved by id off the online list, so the body runs with that lookup stubbed. */
    private void withOwnerOnline(Runnable body) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
            body.run();
        }
    }

    @Test
    void aStrikeSummonRegistersItselfLikeEveryOtherArmedPhase() {
        // The rung has no arming code of its own: the spawn path binds PetSummons only for a tracked() flag
        // set, so a tracked()/payloadArmed() that enumerated phases and missed 'strike' would leave the
        // listener below permanently dark.
        assertTrue(strike(true, true).payloadArmed());
        assertTrue(strike(true, true).tracked());
    }

    @Test
    void aStrikeCancelsTheHitConsumesTheSummonAndFiresTheOwnersImpact() {
        Entity zombie = mock(Entity.class);
        Player victim = mock(Player.class);
        UUID id = arm(zombie, strike(true, true));

        EntityDamageByEntityEvent event = hit(zombie, victim, 7.5);
        withOwnerOnline(() -> listener.onStrike(event));

        verify(event).setCancelled(true);
        verify(dispatch).fireImpact(owner, victim, 7.5); // the damage that would have landed, not a default 0
        verify(zombie).remove();
        assertNull(PetSummons.flags(id), "registries are forgotten before the removal");
        assertNull(GuardianCasts.owner(id));
    }

    @Test
    void aSecondDeliveryOfTheSameHitPaysNothing() {
        Entity zombie = mock(Entity.class);
        Player victim = mock(Player.class);
        arm(zombie, strike(true, true));

        EntityDamageByEntityEvent event = hit(zombie, victim, 7.5);
        withOwnerOnline(() -> {
            listener.onStrike(event);
            listener.onStrike(event); // a re-delivery finds nothing armed
        });

        verify(dispatch, times(1)).fireImpact(any(), any(), anyDouble());
        verify(zombie, times(1)).remove();
    }

    @Test
    void consumeFalseLeavesTheCourierArmedForItsNextHit() {
        Entity zombie = mock(Entity.class);
        Player victim = mock(Player.class);
        UUID id = arm(zombie, strike(false, true));

        EntityDamageByEntityEvent event = hit(zombie, victim, 4.0);
        withOwnerOnline(() -> {
            listener.onStrike(event);
            listener.onStrike(event);
        });

        verify(dispatch, times(2)).fireImpact(owner, victim, 4.0);
        verify(zombie, never()).remove();
        assertNotNull(PetSummons.flags(id), "an unconsumed courier stays armed");
    }

    @Test
    void cancelFalseLetsTheSummonsOwnMeleeLandOnTopOfTheImpact() {
        Entity zombie = mock(Entity.class);
        Player victim = mock(Player.class);
        arm(zombie, strike(true, false));

        EntityDamageByEntityEvent event = hit(zombie, victim, 3.0);
        withOwnerOnline(() -> listener.onStrike(event));

        verify(event, never()).setCancelled(true);
        verify(dispatch).fireImpact(owner, victim, 3.0);
        verify(zombie).remove();
    }

    @Test
    void aHitOnANonPlayerIsNotAStrike() {
        Entity zombie = mock(Entity.class);
        LivingEntity cow = mock(LivingEntity.class);
        UUID id = arm(zombie, strike(true, true));

        EntityDamageByEntityEvent event = hit(zombie, cow, 5.0);
        withOwnerOnline(() -> listener.onStrike(event));

        verifyNoInteractions(dispatch);
        verify(event, never()).setCancelled(true);
        verify(zombie, never()).remove();
        assertNotNull(PetSummons.flags(id), "an unspent courier stays armed");
    }

    @Test
    void anOfflineOwnerLeavesTheSummonArmedAndItsHitIntact() {
        Entity zombie = mock(Entity.class);
        Player victim = mock(Player.class);
        UUID id = arm(zombie, strike(true, true));

        EntityDamageByEntityEvent event = hit(zombie, victim, 6.0);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null);
            listener.onStrike(event);
        }

        verifyNoInteractions(dispatch);
        verify(event, never()).setCancelled(true);
        verify(zombie, never()).remove();
        assertNotNull(PetSummons.flags(id));
    }

    @Test
    void anUnarmedDamagerIsLeftAlone() {
        Entity wildZombie = mock(Entity.class);
        when(wildZombie.getUniqueId()).thenReturn(UUID.randomUUID());
        Player victim = mock(Player.class);

        EntityDamageByEntityEvent event = hit(wildZombie, victim, 5.0);
        listener.onStrike(event);

        verifyNoInteractions(dispatch);
        verify(event, never()).setCancelled(true);
        verify(wildZombie, never()).remove();
    }
}
