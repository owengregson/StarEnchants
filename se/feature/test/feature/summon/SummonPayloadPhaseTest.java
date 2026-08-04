package feature.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.GuardianCasts;
import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The two event-driven payload phases. Detonate REPLACES the vanilla explosion outright — the cancel is what
 * keeps a Self Destruct from griefing terrain and double-damaging, and a summon left alive after firing would
 * detonate again on the next tick.
 */
class SummonPayloadPhaseTest {

    private final List<Entity> fired = new ArrayList<>();
    private final SummonPayloadListener listener =
            new SummonPayloadListener((summon, flags) -> fired.add(summon), () -> true);

    @AfterEach
    void tearDown() {
        PetSummons.clearAll();
        GuardianCasts.clearAll();
    }

    private static SummonFlags phase(String phase) {
        return new SummonFlags(false, false, false, false, false, false, false, 0.0, "", List.of(),
                phase, 40, 4.0, 0.0, "ALL", 0, 0);
    }

    private static UUID arm(Entity entity, String phase) {
        UUID id = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(id);
        PetSummons.bind(id, phase(phase));
        GuardianCasts.bind(id, UUID.randomUUID());
        return id;
    }

    @Test
    void detonateCancelsTheVanillaExplosionAndConsumesTheSummon() {
        Entity tnt = mock(Entity.class);
        UUID id = arm(tnt, "detonate");
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(tnt);

        listener.onExplode(event);

        verify(event).setCancelled(true); // no terrain damage AND no vanilla entity damage
        assertEquals(List.of(tnt), fired);
        verify(tnt).remove();
        assertNull(PetSummons.flags(id), "registries are forgotten before the removal");
        assertNull(GuardianCasts.owner(id));
    }

    @Test
    void anUnarmedExplosionIsLeftAlone() {
        Entity creeper = mock(Entity.class);
        when(creeper.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntity()).thenReturn(creeper);

        listener.onExplode(event);

        verify(event, never()).setCancelled(true);
        assertEquals(List.of(), fired);
    }

    @Test
    void aDetonatePayloadDoesNotFireOnDeath() {
        // The phases are exclusive: a detonate summon that also died would otherwise pay its payload twice.
        LivingEntity creeper = mock(LivingEntity.class);
        arm(creeper, "detonate");
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(creeper);

        listener.onDeath(event);

        assertEquals(List.of(), fired);
    }

    @Test
    void deathFiresTheArmedPayloadExactlyOnce() {
        LivingEntity blaze = mock(LivingEntity.class);
        UUID id = arm(blaze, "death");
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(blaze);

        listener.onDeath(event);
        listener.onDeath(event); // a second delivery must find nothing left to fire

        assertEquals(List.of(blaze), fired);
        assertNull(PetSummons.flags(id));
    }
}
