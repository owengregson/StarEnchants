package feature.combat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.GuardianCasts;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The summon-vs-summoner target guard: a {@link GuardianCasts}-owned summon acquiring its OWN owner is
 * cancelled; every other acquisition (another player, an untracked mob, an un-target) stands untouched.
 */
class SummonTargetGuardListenerTest {

    private final SummonTargetGuardListener listener = new SummonTargetGuardListener();

    private final UUID summonId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        GuardianCasts.clearAll();
    }

    @AfterEach
    void tearDown() {
        GuardianCasts.clearAll();
    }

    private static LivingEntity living(UUID id) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(id);
        return e;
    }

    private EntityTargetLivingEntityEvent targeting(UUID summon, LivingEntity target) {
        Entity mob = living(summon);
        EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getTarget()).thenReturn(target);
        return event;
    }

    @Test
    void ownedSummonTargetingItsOwnerIsCancelled() {
        GuardianCasts.bind(summonId, ownerId);
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        EntityTargetLivingEntityEvent event = targeting(summonId, owner);

        listener.onTarget(event);

        verify(event).setCancelled(true);
    }

    @Test
    void ownedSummonTargetingAnotherPlayerStands() {
        GuardianCasts.bind(summonId, ownerId);
        EntityTargetLivingEntityEvent event = targeting(summonId, living(UUID.randomUUID()));

        listener.onTarget(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void untrackedMobTargetingFreely() {
        EntityTargetLivingEntityEvent event = targeting(summonId, living(ownerId)); // no bind

        listener.onTarget(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void unTargetPassesThrough() {
        GuardianCasts.bind(summonId, ownerId);
        EntityTargetLivingEntityEvent event = targeting(summonId, null); // a forget, not an acquisition

        listener.onTarget(event);

        verify(event, never()).setCancelled(true);
    }
}
