package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Per-(victim, marker) damage marks: bonus lookup, miss paths, no-op guards, clear. */
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
    void clearForgetsAVictimsMarks() {
        DamageMarks.mark(victim, reaper, 0.25, 60_000L);
        DamageMarks.clear(victim);
        assertEquals(0.0, DamageMarks.bonus(victim, reaper));
    }
}
