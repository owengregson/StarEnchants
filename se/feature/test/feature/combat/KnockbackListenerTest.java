package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import feature.combat.KnockbackListener.Path;
import org.junit.jupiter.api.Test;

/**
 * Pins the version-split DECISION of {@link KnockbackListener}: the modern Bukkit event is preferred when
 * present (it is the one post-1.20.6 Paper fires), the legacy destroystokyo event is the fallback, and a
 * server with neither leaves KNOCKBACK_CONTROL inert. The reflective/registration side is matrix-verified.
 */
class KnockbackListenerTest {

    @Test
    void prefersModernWhenPresent() {
        assertEquals(Path.MODERN, KnockbackListener.resolve(true, true), "modern wins even if legacy also present");
        assertEquals(Path.MODERN, KnockbackListener.resolve(true, false));
    }

    @Test
    void fallsBackToLegacy() {
        assertEquals(Path.LEGACY, KnockbackListener.resolve(false, true));
    }

    @Test
    void noneWhenNeitherPresent() {
        assertEquals(Path.NONE, KnockbackListener.resolve(false, false));
    }
}
