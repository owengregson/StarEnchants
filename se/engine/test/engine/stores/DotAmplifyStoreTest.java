package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DOT_AMPLIFY_MARK: a per-cause incoming multiplier that refreshes outright rather than keeping the stronger. */
class DotAmplifyStoreTest {

    private final DotAmplifyStore store = new DotAmplifyStore();

    @Test
    void amplifiesOnlyTheMarkedCausesWithinTheWindow() {
        UUID player = UUID.randomUUID();
        store.amplify(player, 3.0, DotAmplifyStore.CAUSE_WITHER, 0L, 100);
        assertEquals(3.0, store.factor(player, 99L, DotAmplifyStore.CAUSE_WITHER));
        assertEquals(1.0, store.factor(player, 99L, DotAmplifyStore.CAUSE_POISON), "an unmarked cause is neutral");
        assertEquals(1.0, store.factor(player, 100L, DotAmplifyStore.CAUSE_WITHER), "the window closes");
    }

    @Test
    void reMarkingRefreshesOutrightEvenWithAWeakerFactor() {
        UUID player = UUID.randomUUID();
        store.amplify(player, 5.0, DotAmplifyStore.CAUSE_DOT, 0L, 100);
        store.amplify(player, 2.0, DotAmplifyStore.CAUSE_DOT, 50L, 100);
        // Unlike OUTGOING_DEBUFF this is NOT keep-the-stronger: a re-infection is a fresh infection.
        assertEquals(2.0, store.factor(player, 60L, DotAmplifyStore.CAUSE_WITHER));
        assertEquals(2.0, store.factor(player, 149L, DotAmplifyStore.CAUSE_WITHER), "and it carries its own expiry");
    }

    @Test
    void aNeutralOrEmptyMarkIsANoOp() {
        UUID player = UUID.randomUUID();
        store.amplify(player, 1.0, DotAmplifyStore.CAUSE_DOT, 0L, 100);   // a factor of 1 amplifies nothing
        store.amplify(player, 3.0, DotAmplifyStore.CAUSE_DOT, 0L, 0);     // no window
        store.amplify(player, 3.0, 0, 0L, 100);                           // no causes
        assertEquals(1.0, store.factor(player, 0L, DotAmplifyStore.CAUSE_WITHER));
    }

    @Test
    void quitKeepsALiveWindowAndShedsAnElapsedOne() {
        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        store.amplify(live, 3.0, DotAmplifyStore.CAUSE_DOT, 0L, 200);
        store.amplify(stale, 3.0, DotAmplifyStore.CAUSE_DOT, 0L, 40);
        store.evictElapsed(100L);
        assertEquals(3.0, store.factor(live, 100L, DotAmplifyStore.CAUSE_WITHER),
                "an opponent-landed window survives a relog");
        assertEquals(1.0, store.factor(stale, 100L, DotAmplifyStore.CAUSE_WITHER));
    }

    @Test
    void clearForgetsOnePlayer() {
        UUID player = UUID.randomUUID();
        store.amplify(player, 3.0, DotAmplifyStore.CAUSE_DOT, 0L, 200);
        store.clear(player);
        assertEquals(1.0, store.factor(player, 0L, DotAmplifyStore.CAUSE_WITHER));
    }
}
