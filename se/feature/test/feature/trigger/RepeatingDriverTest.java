package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.stores.RepeatStore;
import item.codec.HeroicStat;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/**
 * Unit-pins the §B {@link RepeatingDriver}: arming schedules one entity-repeating task per repeating ability
 * at its own {@code repeatTicks} period (skipping period≤0), records them in the {@link RepeatStore}, and
 * disarming / re-arming cancels the right handles. A recording {@link SchedulerBackend} captures the
 * {@code repeatingEntity} calls without running them; the per-tick fire is covered live in the matrix suite.
 */
class RepeatingDriverTest {

    private static final int REPEATING = 5;
    private static final int GEN = 1;

    private RecordingBackend backend;
    private RepeatStore<TaskHandle> store;
    private RepeatingDriver driver;
    private Player player;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        backend = new RecordingBackend();
        Scheduling.install(backend);
        store = new RepeatStore<>();

        // abilities[3].repeatTicks()=20, abilities[7]=40, abilities[9]=0 (a REPEATING ability with no period).
        Ability[] abilities = new Ability[10];
        for (int i = 0; i < abilities.length; i++) {
            abilities[i] = ability(i, 0);
        }
        abilities[3] = ability(3, 20);
        abilities[7] = ability(7, 40);
        abilities[9] = ability(9, 0);

        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.abilities()).thenReturn(abilities);
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

        assertEquals(2, backend.scheduled.size());
        assertEquals(20L, backend.scheduled.get(0).period);
        assertEquals(40L, backend.scheduled.get(1).period);
        assertTrue(store.has(uuid, 3));
        assertTrue(store.has(uuid, 7));
        assertFalse(store.has(uuid, 9), "a repeatTicks=0 ability is never scheduled");
    }

    @Test
    void duplicateAbilityIdsAreArmedOnce() {
        driver.arm(player, worn(3, 3, 7)); // 3 listed twice (multiplicity) → one task
        assertEquals(2, backend.scheduled.size());
        assertTrue(store.has(uuid, 3));
        assertTrue(store.has(uuid, 7));
    }

    @Test
    void disarmCancelsEveryTaskForThePlayer() {
        driver.arm(player, worn(3, 7));
        List<RecordingHandle> armed = new ArrayList<>(backend.scheduled);

        driver.disarm(uuid);

        assertTrue(armed.stream().allMatch(h -> h.cancelled), "every armed task is cancelled on disarm");
        assertFalse(store.has(uuid, 3));
        assertFalse(store.has(uuid, 7));
    }

    @Test
    void reArmCancelsTheSupersededTasks() {
        driver.arm(player, worn(3, 7));
        RecordingHandle first3 = backend.scheduled.get(0);
        RecordingHandle first7 = backend.scheduled.get(1);

        driver.arm(player, worn(3)); // re-arm with only 3 worn now

        assertTrue(first3.cancelled, "the prior task for 3 is superseded and cancelled");
        assertTrue(first7.cancelled, "7 is no longer worn → cancelled");
        assertEquals(3, backend.scheduled.size(), "one fresh task scheduled on re-arm");
        assertTrue(store.has(uuid, 3));
        assertFalse(store.has(uuid, 7));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    private static Ability ability(int id, int repeatTicks) {
        return new Ability(id, id, SourceKind.ENCHANT, 1 << REPEATING, 1, 100.0, 0, 0, 0L,
                null, new CompiledEffect[0], repeatTicks, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    private static WornState worn(int... repeatingIds) {
        int[][] byTrigger = new int[REPEATING + 1][];
        Arrays.fill(byTrigger, new int[0]);
        byTrigger[REPEATING] = repeatingIds;
        return new WornState(GEN, new BitSet(), new int[0], HeroicStat.NONE, byTrigger, new int[0], new int[0]);
    }

    /** A {@link TaskHandle} that records its own cancellation. */
    private static final class RecordingHandle implements TaskHandle {
        final long period;
        boolean cancelled;

        RecordingHandle(long period) {
            this.period = period;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** A {@link SchedulerBackend} that records {@code repeatingEntity} calls and no-ops everything else. */
    private static final class RecordingBackend implements SchedulerBackend {
        final List<RecordingHandle> scheduled = new ArrayList<>();

        @Override
        public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
            RecordingHandle handle = new RecordingHandle(periodTicks);
            scheduled.add(handle);
            return handle;
        }

        @Override public void onEntity(Entity entity, Runnable task) { }
        @Override public void onEntityLater(Entity entity, long delayTicks, Runnable task) { }
        @Override public void onRegion(Location location, Runnable task) { }
        @Override public void onRegionLater(Location location, long delayTicks, Runnable task) { }
        @Override public TaskHandle repeatingRegion(Location l, long i, long p, Runnable t) { return TaskHandle.CANCELLED; }
        @Override public void onGlobal(Runnable task) { }
        @Override public void onGlobalLater(long delayTicks, Runnable task) { }
        @Override public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) { return TaskHandle.CANCELLED; }
        @Override public void async(Runnable task) { }
    }
}
