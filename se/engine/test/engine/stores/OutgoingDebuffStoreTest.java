package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The ADR-0049 Weaken/Destruction outgoing debuff plus the wave-1d.2 OUTGOING_DEBUFF axes: non-stacking (the
 * stronger window, never the sum) with a monotone expiry, a cause filter, and a per-hit feedback line.
 */
class OutgoingDebuffStoreTest {

    private final OutgoingDebuffStore store = new OutgoingDebuffStore();

    private double percentAt(UUID player, long tick) {
        OutgoingDebuffStore.Debuff debuff = store.active(player, tick);
        return debuff == null ? 0.0 : debuff.percent();
    }

    @Test
    void activeWithinWindowThenElapses() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        assertEquals(15.0, percentAt(player, 99L));
        assertNull(store.active(player, 100L));
    }

    @Test
    void reDebuffNeverSumsAndKeepsStrongerPercent() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        store.weaken(player, 10.0, 0L, 100); // a second attacker — must NOT compound to 25
        assertEquals(15.0, percentAt(player, 50L), "non-stacking: the stronger percent, never the sum");
    }

    @Test
    void weakerLongerReDebuffExtendsButKeepsStrongerPercent() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        store.weaken(player, 10.0, 0L, 200);
        assertEquals(15.0, percentAt(player, 150L), "stronger percent + later expiry, component-wise");
        assertNull(store.active(player, 200L));
    }

    @Test
    void nonPositiveIsNoOp() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 0.0, 0L, 100);
        store.weaken(player, 15.0, 0L, 0);
        store.debuff(player, 15.0, 0, "", 0L, 100); // an empty cause mask prices nothing, so it arms nothing
        assertNull(store.active(player, 0L));
    }

    @Test
    void evictElapsedDropsOnlyExpired() {
        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        store.weaken(live, 15.0, 0L, 200);
        store.weaken(stale, 15.0, 0L, 40);
        store.evictElapsed(100L);
        assertEquals(15.0, percentAt(live, 100L));
        assertNull(store.active(stale, 100L));
    }

    @Test
    void weakenIsUnfilteredAndSilent() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        OutgoingDebuffStore.Debuff debuff = store.active(player, 50L);
        assertTrue(debuff.covers(OutgoingDebuffStore.CAUSE_MELEE));
        assertTrue(debuff.covers(OutgoingDebuffStore.CAUSE_PROJECTILE));
        assertTrue(debuff.feedback().isEmpty());
    }

    @Test
    void aFilteredDebuffPricesOnlyItsOwnCause() {
        UUID player = UUID.randomUUID();
        store.debuff(player, 50.0, OutgoingDebuffStore.CAUSE_PROJECTILE, "unfocused", 0L, 100);
        OutgoingDebuffStore.Debuff debuff = store.active(player, 50L);
        assertTrue(debuff.covers(OutgoingDebuffStore.CAUSE_PROJECTILE));
        assertFalse(debuff.covers(OutgoingDebuffStore.CAUSE_MELEE), "a bow nerf must leave melee alone");
        assertEquals("unfocused", debuff.feedback());
    }

    @Test
    void theStrongerWindowKeepsItsOwnFilterAndFeedback() {
        UUID player = UUID.randomUUID();
        store.debuff(player, 50.0, OutgoingDebuffStore.CAUSE_PROJECTILE, "bow", 0L, 100);
        store.debuff(player, 20.0, OutgoingDebuffStore.CAUSE_MELEE, "melee", 0L, 200);
        OutgoingDebuffStore.Debuff debuff = store.active(player, 150L);
        assertEquals(50.0, debuff.percent());
        assertEquals("bow", debuff.feedback(), "a live window is one authored debuff, never a chimera of two");
        assertFalse(debuff.covers(OutgoingDebuffStore.CAUSE_MELEE));
        assertNull(store.active(player, 200L), "the later expiry still rides");
    }
}
