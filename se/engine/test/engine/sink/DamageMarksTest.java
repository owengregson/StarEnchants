package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Per-(victim, marker) damage marks: bonus lookup, the reverse-lookup of a marker's victims, guards, clear. */
class DamageMarksTest {

    private final UUID victim = UUID.randomUUID();
    private final UUID reaper = UUID.randomUUID();

    @AfterEach
    void clean() {
        DamageMarks.clearAll();
    }

    @Test
    void aMarkGivesOnlyItsMarkerTheBonus() {
        DamageMarks.mark(victim, reaper, 0.25, 60_000L);
        assertEquals(0.25, DamageMarks.bonus(victim, reaper));
        assertEquals(0.0, DamageMarks.bonus(victim, UUID.randomUUID())); // a different attacker gets nothing
    }

    @Test
    void anUnmarkedVictimGivesNoBonus() {
        assertEquals(0.0, DamageMarks.bonus(victim, reaper)); // no marks for this victim at all
    }

    @Test
    void guardsRejectNoOpMarks() {
        DamageMarks.mark(null, reaper, 0.25, 60_000L);
        DamageMarks.mark(victim, null, 0.25, 60_000L);
        DamageMarks.mark(victim, reaper, 0.0, 60_000L); // zero fraction
        DamageMarks.mark(victim, reaper, 0.25, 0L);     // zero duration
        assertEquals(0.0, DamageMarks.bonus(victim, reaper));
    }

    @Test
    void markedReturnsEveryVictimAMarkerHasMarkedAndNoOthers() {
        UUID otherVictim = UUID.randomUUID();
        UUID otherReaper = UUID.randomUUID();
        DamageMarks.mark(victim, reaper, 0.25, 60_000L);
        DamageMarks.mark(otherVictim, reaper, 0.25, 60_000L);
        DamageMarks.mark(otherVictim, otherReaper, 0.25, 60_000L); // a different marker's mark must not leak in

        assertEquals(Set.of(victim, otherVictim), DamageMarks.marked(reaper));
        assertEquals(Set.of(otherVictim), DamageMarks.marked(otherReaper));
    }

    @Test
    void markedIsEmptyForAnUnknownMarkerAndNullIsSafe() {
        assertTrue(DamageMarks.marked(UUID.randomUUID()).isEmpty());
        assertTrue(DamageMarks.marked(null).isEmpty());
    }

    @Test
    void clearForgetsAVictimsMarks() {
        DamageMarks.mark(victim, reaper, 0.25, 60_000L);
        DamageMarks.clear(victim);
        assertEquals(0.0, DamageMarks.bonus(victim, reaper));
    }
}
