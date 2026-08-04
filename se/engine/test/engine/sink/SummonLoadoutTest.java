package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import platform.sched.Scheduling;
import platform.text.Colors;
import schema.spec.PotionLoadout;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.SyncSchedulerBackend;

/**
 * The summon loadout the three spawners share: a POTION_EFFECT list entry carries its authored level as a
 * packed amplifier (an unpacked read would apply a level as if it were a potion id), SPAWN_SWARM reaches the
 * SAME name/loadout helpers GUARD and SPAWN_ENTITY do, and an armed payload's pulse self-cancels once its
 * summon is gone.
 */
class SummonLoadoutTest {

    private RecordingSink sink;
    private World world;
    private Location at;
    private LivingEntity spawned;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        sink = new RecordingSink(Envs.sink().build());
        sink.spawnType = EntityType.ZOMBIE;
        world = mock(World.class);
        at = mock(Location.class);
        when(at.clone()).thenReturn(at);
        when(at.add(anyDouble(), anyDouble(), anyDouble())).thenReturn(at);
        when(at.getWorld()).thenReturn(world);
        spawned = mock(LivingEntity.class);
        when(spawned.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spawned.isValid()).thenReturn(true);
        when(world.spawnEntity(any(Location.class), any(EntityType.class))).thenReturn(spawned);
    }

    @AfterEach
    void tearDown() {
        PetSummons.clearAll();
        GuardianCasts.clearAll();
    }

    @Test
    void aLoadoutEntryIsHeldAtTheLevelItPacks() {
        // Two entries with DIFFERENT levels: a reader that unpacks only the first, or reuses one amplifier for
        // the whole loop, still applies two effects — only the per-entry amplifier tells the two apart.
        PotionEffectType speed = mock(PotionEffectType.class);
        PotionEffectType strength = mock(PotionEffectType.class);
        sink.potions.put(3, speed);
        sink.potions.put(7, strength);

        sink.guard(null, at, 1, 1, 0, "", null, 0.0, 0.0,
                List.of(PotionLoadout.pack(3, 2), PotionLoadout.pack(7, 0)));
        sink.flush();

        ArgumentCaptor<PotionEffect> applied = ArgumentCaptor.forClass(PotionEffect.class);
        verify(spawned, times(2)).addPotionEffect(applied.capture());
        List<PotionEffect> effects = applied.getAllValues();
        assertEquals(speed, effects.get(0).getType());
        assertEquals(2, effects.get(0).getAmplifier(), "level 3 reaches the summon as amplifier 2");
        assertEquals(strength, effects.get(1).getType());
        assertEquals(0, effects.get(1).getAmplifier(), "a bare entry stays level 1");
    }

    @Test
    void spawnSwarmNamesAndBuffsItsSummonsThroughTheSameHelpers() {
        // The third spawner never had name/effects; without them a ring of minions is anonymous and unbuffed,
        // which no unit below the sink can observe (the effect only emits the intent).
        PotionEffectType speed = mock(PotionEffectType.class);
        sink.potions.put(3, speed);

        sink.spawnSwarm(at, 1, 1, 0.5, 1.2, 0, 1.0, null, 16.0, "&bMinion",
                List.of(PotionLoadout.pack(3, 1)));
        sink.flush();

        verify(spawned).setCustomNameVisible(true);
        ArgumentCaptor<PotionEffect> applied = ArgumentCaptor.forClass(PotionEffect.class);
        verify(spawned).addPotionEffect(applied.capture());
        assertEquals(speed, applied.getValue().getType());
        assertEquals(1, applied.getValue().getAmplifier());
    }

    @Test
    void aSummonNamesOwnerTokenIsFilledFromTheOwnerTheSpawnerThreads() {
        // The owner reaches the nameplate only if the spawner passes it down; dropping it anywhere silently
        // strips {OWNER} from every summon instead of failing, and no unit below the sink can see the name.
        UUID ownerId = UUID.randomUUID();
        Player owner = mock(Player.class);
        when(owner.getName()).thenReturn("Notch");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);

            sink.guard(null, at, 1, 1, 0, "&b&l{OWNER}'s Guardian", ownerId, 0.0, 0.0, List.of());
            sink.flush();
        }

        verify(spawned).setCustomName(Colors.translate("&b&lNotch's Guardian"));
    }

    @Test
    void anArmedPeriodicPayloadPulsesWhileItLivesAndSelfCancelsWhenItIsGone() {
        // Paper's fallback scheduler does not stop a repeating task when the entity is removed, so without the
        // liveness guard the pulse fires forever on a dead summon — and its registry rows leak with it.
        RecordingSchedulerBackend sched = new RecordingSchedulerBackend();
        Scheduling.install(sched);
        List<Entity> fired = new ArrayList<>();
        RecordingSink payloadSink = new RecordingSink(
                Envs.sink().payloads((summon, flags) -> fired.add(summon)).build());
        payloadSink.spawnType = EntityType.ZOMBIE;
        UUID id = spawned.getUniqueId();

        payloadSink.spawnSummon(at, 1, 1, 0, 0.0, UUID.randomUUID(), null, periodic());
        payloadSink.flush();

        assertNotNull(PetSummons.flags(id), "a payload summon is tracked so its phases can find it");
        assertEquals(1, sched.repeating.size());
        RecordingSchedulerBackend.Repeat pulse = sched.repeating.get(0);
        assertEquals(40L, pulse.periodTicks);

        pulse.task.run();
        assertEquals(List.of(spawned), fired, "a live summon pulses its payload");

        when(spawned.isValid()).thenReturn(false);
        pulse.task.run();
        assertEquals(1, fired.size(), "a dead summon fires nothing");
        assertTrue(pulse.isCancelled());
        assertNull(PetSummons.flags(id), "the self-cancel forgets the registries it was spawned into");
        assertNull(GuardianCasts.owner(id));
    }

    /** SPAWN_ENTITY's defaults with a periodic payload armed — the shape the sink routes off the plain path. */
    private static SummonFlags periodic() {
        return SummonFlags.NONE.withPayload(SummonFlags.PHASE_PERIODIC, 40, 4.0, 0.0, "ALL", 0, 0);
    }

    @Test
    void aScatteredSpawnStillProducesEverySummonWhenNoCellIsStandable() {
        // scatter air-scans so a TNT never spawns inside stone, but a walled-in cast must still detonate:
        // failing CLOSED here would silently drop the whole summon instead of falling back to the origin.
        sink.safe = loc -> false;

        sink.spawnSummon(at, 1, 4, 0, 0.0, null, null, scattered());
        sink.flush();

        verify(world, times(4)).spawnEntity(any(Location.class), any(EntityType.class));
        assertTrue(sink.safeChecks > 0, "the placement actually probed for a free cell");
    }

    /** SPAWN_ENTITY's defaults with scatter armed. */
    private static SummonFlags scattered() {
        return SummonFlags.NONE.withPayload(SummonFlags.PHASE_NONE, 40, 4.0, 0.0, "ALL", 0, 3);
    }
}
