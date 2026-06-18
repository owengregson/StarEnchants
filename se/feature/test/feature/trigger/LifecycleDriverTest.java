package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import compile.model.SourceKind;
import compile.model.StableKeyIndex;
import item.codec.HeroicStat;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit-pins the §B {@link LifecycleDriver} diff (ADR-0022): each {@link LifecycleDriver#refresh} compares the
 * player's currently-worn HELD/PASSIVE abilities (by STABLE key) against what it last saw, and asks
 * {@link TriggerDispatch#fireLifecycle} to START the arrivals and STOP the departures. A mocked dispatch
 * captures the {@code (stops, starts)} lists; the actual apply/remove of the buff is covered live in the
 * matrix suite. No scheduler is involved — the driver only computes the transition.
 */
class LifecycleDriverTest {

    private static final int HELD = 11;
    private static final int PASSIVE = 8;
    private static final int GEN = 1;
    private static final int WIDTH = 12; // byTrigger arrays sized to cover HELD(11)

    private Ability[] abilities;
    private StableKeyIndex keys;
    private TriggerDispatch dispatch;
    private LifecycleDriver driver;
    private Player player;

    @BeforeEach
    void setUp() {
        abilities = new Ability[10];
        List<String> keyList = new ArrayList<>();
        for (int i = 0; i < abilities.length; i++) {
            abilities[i] = ability(i);
            keyList.add("enchants/e" + i + "/1");
        }
        keys = new StableKeyIndex(keyList);

        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.abilities()).thenReturn(abilities);
        when(snapshot.generation()).thenReturn(GEN);
        when(snapshot.stableKeys()).thenReturn(keys);
        when(snapshot.byStableKey(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            int id = keys.idOf(inv.getArgument(0));
            return id < 0 ? null : abilities[id];
        });
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);

        dispatch = mock(TriggerDispatch.class);
        driver = new LifecycleDriver(dispatch, content, HELD, PASSIVE);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void firstEquipStartsEveryWornBuffAndStopsNothing() {
        driver.refresh(player, worn(held(3), passive(7)));

        Captured c = capture();
        assertTrue(c.stops.isEmpty(), "nothing was previously worn, so nothing stops");
        assertEquals(List.of(abilities[3], abilities[7]), c.starts); // HELD collected before PASSIVE
    }

    @Test
    void unequipStopsThePreviouslyStartedBuffs() {
        driver.refresh(player, worn(held(3), passive(7))); // start 3 + 7
        driver.refresh(player, worn());                    // everything removed

        Captured second = captureNth(1);
        assertEquals(2, second.stops.size());
        assertTrue(second.stops.contains(abilities[3]) && second.stops.contains(abilities[7]));
        assertTrue(second.starts.isEmpty());
    }

    @Test
    void swappingASourceStopsTheOldAndStartsTheNew() {
        driver.refresh(player, worn(held(3))); // start 3
        driver.refresh(player, worn(held(5))); // 3 leaves, 5 arrives

        Captured second = captureNth(1);
        assertEquals(List.of(abilities[3]), second.stops);
        assertEquals(List.of(abilities[5]), second.starts);
    }

    @Test
    void listMultiplicityIsDedupedToOneEntryPerKey() {
        driver.refresh(player, worn(held(3, 3), passive(3))); // same ability on three worn slots

        Captured c = capture();
        assertEquals(List.of(abilities[3]), c.starts, "one start per stable key, not per worn piece");
    }

    @Test
    void anUnchangedRefreshStartsAndStopsNothing() {
        driver.refresh(player, worn(held(3)));
        driver.refresh(player, worn(held(3))); // identical worn state

        Captured second = captureNth(1);
        assertTrue(second.stops.isEmpty() && second.starts.isEmpty(), "a no-op refresh fires no transition");
    }

    @Test
    void aStaleWornStateIsSkipped() {
        WornState stale = new WornState(GEN + 1, new BitSet(), new int[0], HeroicStat.NONE,
                byTrigger(held(3)), new int[0], new int[0]);
        driver.refresh(player, stale);
        verify(dispatch, never()).fireLifecycle(eq(player), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void clearForgetsTrackingSoTheNextEquipReStartsWithoutStopping() {
        driver.refresh(player, worn(held(3))); // start 3
        driver.clear(player.getUniqueId());    // player quit — tracking dropped, no teardown
        driver.refresh(player, worn(held(3))); // rejoin: 3 is "new" again

        Captured third = captureNth(1); // the post-clear refresh
        assertTrue(third.stops.isEmpty(), "after clear, the prior start is forgotten (no stop)");
        assertEquals(List.of(abilities[3]), third.starts, "and the worn buff re-starts");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    private Captured capture() {
        return captureNth(0);
    }

    @SuppressWarnings("unchecked")
    private Captured captureNth(int index) {
        ArgumentCaptor<List<Ability>> stops = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Ability>> starts = ArgumentCaptor.forClass(List.class);
        verify(dispatch, times(index + 1)).fireLifecycle(eq(player), stops.capture(), starts.capture());
        return new Captured(stops.getAllValues().get(index), starts.getAllValues().get(index));
    }

    private record Captured(List<Ability> stops, List<Ability> starts) {
    }

    private static Ability ability(int id) {
        // Trigger mask spans HELD + PASSIVE so the same ability could fire on either; the driver picks by
        // which byTrigger array carries the id (the worn() fixture decides that).
        int mask = (1 << HELD) | (1 << PASSIVE);
        return new Ability(id, id, SourceKind.ENCHANT, mask, 1, 100.0, 0, 0, 0L,
                null, new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    /** A worn-state fixture; HELD/PASSIVE candidate id lists are supplied via {@link #held}/{@link #passive}. */
    private static WornState worn(int[]... slots) {
        return new WornState(GEN, new BitSet(), new int[0], HeroicStat.NONE, byTrigger(slots),
                new int[0], new int[0]);
    }

    private static int[][] byTrigger(int[]... slots) {
        int[][] byTrigger = new int[WIDTH][];
        Arrays.fill(byTrigger, new int[0]);
        for (int[] slot : slots) {
            int trigger = slot[0];
            byTrigger[trigger] = Arrays.copyOfRange(slot, 1, slot.length);
        }
        return byTrigger;
    }

    /** A HELD candidate list: {@code held(3, 3)} = ability 3 on two worn pieces. */
    private static int[] held(int... ids) {
        return prefixed(HELD, ids);
    }

    private static int[] passive(int... ids) {
        return prefixed(PASSIVE, ids);
    }

    private static int[] prefixed(int trigger, int[] ids) {
        int[] out = new int[ids.length + 1];
        out[0] = trigger;
        System.arraycopy(ids, 0, out, 1, ids.length);
        return out;
    }
}
