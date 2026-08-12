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
    private RepeatStore<RepeatingDriver.Armed> store;
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
        abilities[7] = ability(7, 40);
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
        assertEquals(40L, backend.repeating.get(1).periodTicks);
        // Unset repeat-delay: the first run stays one full period out, the shape every shipped file was written
        // against — a driver that defaulted it to 0 would fire every REPEATING ability on the equip tick.
        assertEquals(20L, backend.repeating.get(0).initialDelayTicks);
        assertEquals(40L, backend.repeating.get(1).initialDelayTicks);
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
    void reArmCancelsOnlyWhatIsNoLongerWorn() {
        driver.arm(player, worn(3, 7));
        RecordingSchedulerBackend.Repeat first3 = backend.repeating.get(0);
        RecordingSchedulerBackend.Repeat first7 = backend.repeating.get(1);

        driver.arm(player, worn(3)); // re-arm with only 3 worn now

        assertFalse(first3.isCancelled(), "3 is still worn — its task keeps running");
        assertTrue(first7.isCancelled(), "7 is no longer worn → cancelled");
        assertEquals(2, backend.repeating.size(), "the survivor is kept, not re-scheduled");
        assertTrue(store.has(uuid, 3));
        assertFalse(store.has(uuid, 7));
    }

    @Test
    void anUnchangedReArmTouchesNothing() {
        // arm() fires on every equipment refresh, a plain hotbar scroll included. A task whose first run is one
        // full period out never reaches it if each scroll restarts the phase — which is how a 20t ward holding
        // a 60t immunity window open goes inert mid-fight while the mask still says "Immune to …".
        driver.arm(player, worn(3, 7));
        List<RecordingSchedulerBackend.Repeat> armed = new ArrayList<>(backend.repeating);

        driver.arm(player, worn(3, 7));
        driver.arm(player, worn(3, 7));

        assertTrue(armed.stream().noneMatch(RecordingSchedulerBackend.Repeat::isCancelled));
        assertEquals(2, backend.repeating.size(), "no task is re-scheduled by a no-op refresh");
    }

    @Test
    void aNewlyWornAbilityIsArmedWithoutDisturbingItsSiblings() {
        driver.arm(player, worn(3));
        RecordingSchedulerBackend.Repeat first3 = backend.repeating.get(0);

        driver.arm(player, worn(3, 7));

        assertFalse(first3.isCancelled());
        assertEquals(2, backend.repeating.size());
        assertEquals(40L, backend.repeating.get(1).periodTicks, "only 7 is freshly scheduled");
        assertTrue(store.has(uuid, 7));
    }

    @Test
    void aRetiredTaskIsReArmedInsteadOfAssumedLive() {
        // On Folia an entity task is RETIRED when its entity is removed, which a player death does; nothing
        // disarms on death or respawn. Reading liveness off the store instead of the handle left every
        // REPEATING ward dead for the rest of the session, and only a retiring backend can show it.
        driver.arm(player, worn(3));
        RecordingSchedulerBackend.Repeat armed = backend.repeating.get(0);

        armed.retire();
        driver.arm(player, worn(3));

        assertEquals(2, backend.repeating.size(), "a retired task is rescheduled, not kept");
        assertFalse(backend.repeating.get(1).isCancelled());
        assertEquals(20L, backend.repeating.get(1).periodTicks);
        assertTrue(store.has(uuid, 3));
    }

    @Test
    void aReloadReschedulesSoAnEditedPeriodTakesEffect() {
        // ReloadModule re-arms every online player for exactly this reason. The period is read once, at
        // schedule time, so a task kept across the swap would run on the OLD cadence forever — and the same
        // dense id can name different content after a recompile.
        ContentHolder held = mock(ContentHolder.class);
        when(held.snapshot()).thenReturn(Snapshots.snapshot().generation(1)
                .abilities(ability(0, 20)).build());
        RepeatingDriver reloaded = new RepeatingDriver(mock(TriggerDispatch.class), held, REPEATING, store);
        reloaded.arm(player, worn(0));

        when(held.snapshot()).thenReturn(Snapshots.snapshot().generation(2)
                .abilities(ability(0, 100)).build());
        reloaded.arm(player, worn(0));

        assertTrue(backend.repeating.get(0).isCancelled(), "the task armed against the old snapshot is replaced");
        assertEquals(2, backend.repeating.size());
        assertEquals(100L, backend.repeating.get(1).periodTicks, "the re-armed task runs the edited period");
    }

    @Test
    void anAuthoredRepeatDelayMovesTheFirstRunOffThePeriodAndZeroClampsToTheNextTick() {
        // R-QC35b. The period is untouched either way — only the FIRST run moves.
        Ability[] abilities = new Ability[3];
        abilities[0] = Abilities.ability().id(0).defId(0).trigger(REPEATING).repeatTicks(100).repeatDelay(5).build();
        abilities[1] = Abilities.ability().id(1).defId(1).trigger(REPEATING).repeatTicks(100).repeatDelay(0).build();
        abilities[2] = Abilities.ability().id(2).defId(2).trigger(REPEATING).repeatTicks(100).build();
        ContentHolder held = mock(ContentHolder.class);
        when(held.snapshot()).thenReturn(Snapshots.snapshot().abilities(abilities).build());
        RepeatingDriver delayed = new RepeatingDriver(mock(TriggerDispatch.class), held, REPEATING, store);

        delayed.arm(player, worn(0, 1, 2));

        assertEquals(5L, backend.repeating.get(0).initialDelayTicks);
        assertEquals(100L, backend.repeating.get(0).periodTicks);
        assertEquals(1L, backend.repeating.get(1).initialDelayTicks, "0 clamps to the earliest tick a task can hold");
        assertEquals(100L, backend.repeating.get(2).initialDelayTicks, "unset still means one full period");
    }

    private static Ability ability(int id, int repeatTicks) {
        return Abilities.ability().id(id).defId(id).trigger(REPEATING).repeatTicks(repeatTicks).build();
    }

    private static WornState worn(int... repeatingIds) {
        return WornStates.worn().gen(GEN).byTrigger(REPEATING, repeatingIds).build();
    }
}
