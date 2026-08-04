package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * The hit-gated fuse (ADR-0052) once a payload can be armed on the same summon: the payload REPLACES the
 * hardcoded explosion, so a Plague Carrier does not pay both an authored blast and a 6.0-power one.
 */
class PetSummonFuseTest {

    private RecordingSchedulerBackend sched;
    private final List<Entity> fired = new ArrayList<>();
    private Entity creeper;
    private World world;
    private UUID id;

    @BeforeEach
    void setUp() {
        sched = new RecordingSchedulerBackend();
        Scheduling.install(sched);
        creeper = mock(Entity.class);
        world = mock(World.class);
        id = UUID.randomUUID();
        when(creeper.getUniqueId()).thenReturn(id);
        when(creeper.isValid()).thenReturn(true);
        when(creeper.getWorld()).thenReturn(world);
        when(creeper.getLocation()).thenReturn(mock(Location.class));
        GuardianCasts.bind(id, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        PetSummons.clearAll();
        GuardianCasts.clearAll();
    }

    private void hit(SummonFlags flags) {
        PetSummons.bind(id, flags);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(creeper);
        when(event.getDamager()).thenReturn(mock(Player.class));
        new PetSummonListener((summon, f) -> fired.add(summon), () -> true).onHit(event);
        sched.runDelayed();
    }

    private static SummonFlags flags(String phase) {
        return SummonFlags.of(false, false, false, false, false, true /* detonateOnPlayerHit */, false,
                0.0, "", List.of()).withPayload(phase, 40, 4.0, 0.0, "ALL", 0, 0);
    }

    @Test
    void anArmedFuseRunsThePayloadAndNoVanillaExplosion() {
        hit(flags("detonate"));

        assertEquals(List.of(creeper), fired);
        verify(world, never()).createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(creeper).remove();
        assertNull(GuardianCasts.owner(id), "the fuse forgets the registries before removing the summon");
    }

    @Test
    void anUnarmedFuseKeepsTheEntityOnlyExplosion() {
        hit(flags("none"));

        assertEquals(List.of(), fired);
        verify(world).createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat(),
                org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(false));
        verify(creeper).remove();
    }
}
