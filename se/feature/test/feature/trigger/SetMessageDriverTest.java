package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.SetDef;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import item.codec.HeroicStat;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the set equip/remove announcer — it diffs the active sets across refreshes (keyed by stable
 * key), fires the authored message on a transition, gates on the per-set {@code announce} toggle, and skips a
 * stale worn state. Mirrors {@code LifecycleDriverTest}: a mocked snapshot/library, a capturing sender.
 */
class SetMessageDriverTest {

    private static final int GEN = 1;

    private Player player;
    private List<String> sent;
    private SetMessageDriver driver;

    @BeforeEach
    void setUp() {
        // setId 0 = sets/devil (announce on), setId 1 = sets/quiet (announce off).
        StableKeyIndex keys = new StableKeyIndex(List.of("sets/devil", "sets/quiet"));
        Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.generation()).thenReturn(GEN);
        when(snapshot.stableKeys()).thenReturn(keys);

        Library library = mock(Library.class);
        when(library.setDefOf("sets/devil")).thenReturn(setDef("sets/devil", true, "EQUIP devil", "REMOVE devil"));
        when(library.setDefOf("sets/quiet")).thenReturn(setDef("sets/quiet", false, "EQUIP quiet", "REMOVE quiet"));

        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(snapshot);
        when(content.library()).thenReturn(library);

        sent = new ArrayList<>();
        driver = new SetMessageDriver(content, (p, msg) -> sent.add(msg));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void announcesEquipWhenASetCompletesAndRemoveWhenItDrops() {
        driver.refresh(player, worn(0)); // devil completes
        assertEquals(List.of("EQUIP devil"), sent);

        driver.refresh(player, worn()); // devil drops below threshold
        assertEquals(List.of("EQUIP devil", "REMOVE devil"), sent);
    }

    @Test
    void aStillCompleteSetDoesNotReAnnounce() {
        driver.refresh(player, worn(0));
        driver.refresh(player, worn(0)); // still active — no second equip line
        assertEquals(List.of("EQUIP devil"), sent);
    }

    @Test
    void aSetWithAnnounceOffIsSilent() {
        driver.refresh(player, worn(1)); // quiet completes (announce off)
        driver.refresh(player, worn());  // quiet drops
        assertTrue(sent.isEmpty(), () -> "announce:false set must be silent; got " + sent);
    }

    @Test
    void aStaleWornStateIsSkipped() {
        driver.refresh(player, wornGen(GEN + 1, 0)); // ids index a different snapshot
        assertTrue(sent.isEmpty());
    }

    private static SetDef setDef(String key, boolean announce, String equip, String remove) {
        return new SetDef(key, key, "", null, 1, List.of(), List.of(), null, List.of(), List.of(),
                Map.of(), Map.of(), announce, equip, remove, schema.diag.Source.ofFile("test"));
    }

    private WornState worn(int... activeSetIds) {
        return wornGen(GEN, activeSetIds);
    }

    private static WornState wornGen(int gen, int... activeSetIds) {
        BitSet sets = new BitSet();
        for (int id : activeSetIds) {
            sets.set(id);
        }
        return new WornState(gen, sets, new int[0], HeroicStat.NONE, new int[0][], new int[0], new int[0]);
    }
}
