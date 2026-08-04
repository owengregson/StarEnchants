package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** The {@code dy} anchor arithmetic shared by SOUND and PARTICLE's location-anchored halves (wave 2e.2). */
class AnchorsTest {

    @Test
    void noOffsetHandsBackTheSamePoint() {
        // Every line authored before dy existed passes 0. Those must reach the sink with the point they always
        // had — a defensive copy here would be a per-cue allocation on the combat path for no behaviour.
        Location loc = new Location(null, 1.0, 2.0, 3.0);
        assertSame(loc, Anchors.raised(loc, 0.0));
    }

    @Test
    void anOffsetRaisesACopyAndLeavesTheOriginalAlone() {
        Location loc = new Location(null, 1.0, 2.0, 3.0);
        Location raised = Anchors.raised(loc, 4.0);
        assertNotSame(loc, raised);
        assertEquals(6.0, raised.getY());
        assertEquals(1.0, raised.getX());
        assertEquals(3.0, raised.getZ());
        // The activation location is shared by every effect on the walk; moving it in place would drag the
        // siblings' anchors along with this one.
        assertEquals(2.0, loc.getY());
    }

    @Test
    void aNegativeOffsetLowersIt() {
        assertEquals(-1.5, Anchors.raised(new Location(null, 0.0, 0.5, 0.0), -2.0).getY());
    }
}
