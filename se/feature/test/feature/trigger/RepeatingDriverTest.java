package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.stores.RepeatStore;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import testfx.Abilities;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;
import testfx.WornStates;

/**
 * Unit-pins the §B {@link RepeatingDriver}: arming schedules one entity-repeating task per repeating ability
 * at its own {@code repeatTicks} period (skipping period≤0), records them in the {@link RepeatStore}, and
 * disarming / re-arming cancels the right handles. A {@link RecordingSchedulerBackend} captures the
 * {@code repeatingEntity} calls without running them; the per-tick fire is covered live in the matrix suite.
 */
class RepeatingDriverTest {

    private static final int REPEATING = 5;
    private static final int GEN = 1;

    private RecordingSchedulerBackend backend;
    private RepeatStore<TaskHandle> store;
    private RepeatingDriver driver;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        store = new RepeatStore<>();

        // abilities[3].repeatTicks()=20, abilities[7]=40, abilities[9]=0 (a REPEATING ability with no period).
        Ability[] abilities = new Ability[10];
        for (int i = 0; i < abilities.length; i++) {
            abilities[i] = ability(i, 0);
        }
        abilities[3] = ability(3, 20);
        abilities[7] = ability(7, 40, 5);
        abilities[9] = ability(9, 0);

        Snapshot snapshot = Snapshots.snapshot().abilities(abilities).build();
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);

        driver = new RepeatingDriver(mock(TriggerDispatch.class), content, REPEATING, store);

        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
    }

    @Test
    void armSchedulesOneTaskPerRepeatingAbilityAtItsPeriodAndSkipsZero() {
        driver.arm(player, worn(3, 7, 9)); // 9 has period 0 → skipped

        assertEquals(2, backend.repeating.size());
        assertEquals(20L, backend.repeating.get(0).periodTicks);
        assertEquals(20L, backend.repeating.get(0).initialDelayTicks);
        assertEquals(40L, backend.repeating.get(1).periodTicks);
        assertEquals(5L, backend.repeating.get(1).initialDelayTicks);
        assertTrue(store.has(uuid, 3));
        assertTrue(store.has(uuid, 7));
        assertFalse(store.has(uuid, 9), "a repeatTicks=0 ability is never scheduled");
    }

    @Test
    void duplicateAbilityIdsAreArmedOnce() {
        driver.arm(player, worn(3, 3, 7)); // 3 listed twice (multiplicity) → one task
        assertEquals(2, backend.repeating.size());
        assertTrue(store.has(uuid, 3));
        assertTrue(store.has(uuid, 7));
    }

    @Test
    void disarmCancelsEveryTaskForThePlayer() {
        driver.arm(player, worn(3, 7));
        List<RecordingSchedulerBackend.Repeat> armed = new ArrayList<>(backend.repeating);

        driver.disarm(uuid);

        assertTrue(armed.stream().allMatch(RecordingSchedulerBackend.Repeat::isCancelled),
                "every armed task is cancelled on disarm");
        assertFalse(store.has(uuid, 3));
        assertFalse(store.has(uuid, 7));
    }

    @Test
    void reArmCancelsTheSupersededTasks() {
        driver.arm(player, worn(3, 7));
        RecordingSchedulerBackend.Repeat first3 = backend.repeating.get(0);
        RecordingSchedulerBackend.Repeat first7 = backend.repeating.get(1);

        driver.arm(player, worn(3)); // re-arm with only 3 worn now

        assertTrue(first3.isCancelled(), "the prior task for 3 is superseded and cancelled");
        assertTrue(first7.isCancelled(), "7 is no longer worn → cancelled");
        assertEquals(3, backend.repeating.size(), "one fresh task scheduled on re-arm");
        assertTrue(store.has(uuid, 3));
        assertFalse(store.has(uuid, 7));
    }

    private static Ability ability(int id, int repeatTicks) {
        return Abilities.ability().id(id).defId(id).trigger(REPEATING).repeatTicks(repeatTicks).build();
    }

    private static Ability ability(int id, int repeatTicks, int initialDelayTicks) {
        return Abilities.ability().id(id).defId(id).trigger(REPEATING).repeatTicks(repeatTicks)
                .repeatInitialDelayTicks(initialDelayTicks).build();
    }

    private static WornState worn(int... repeatingIds) {
        return WornStates.worn().gen(GEN).byTrigger(REPEATING, repeatingIds).build();
    }
}
