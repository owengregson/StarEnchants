package feature.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pure guard helpers: NearGuard's command-word parse and SplashHealGuard's healing-name predicate. */
class MaskGuardHelpersTest {

    // ── NearGuard.commandWord ────────────────────────────────────────────────────────────────────────────
    @Test
    void commandWordStripsSlashLowercasesAndTakesTheFirstToken() {
        assertEquals("near", NearGuard.commandWord("/near"));
        assertEquals("near", NearGuard.commandWord("/Near SomePlayer"));   // first token, lowercased
        assertEquals("near", NearGuard.commandWord("near"));                // slash optional
        assertEquals("who", NearGuard.commandWord("/who arg1 arg2"));
    }

    @Test
    void commandWordHandlesEmptyAndNull() {
        assertEquals("", NearGuard.commandWord(null));
        assertEquals("", NearGuard.commandWord(""));
        assertEquals("", NearGuard.commandWord("/"));
    }

    // ── SplashHealGuard.isHealingName ────────────────────────────────────────────────────────────────────
    @Test
    void healingNamesMatchAcrossErasCaseInsensitively() {
        assertTrue(SplashHealGuard.isHealingName("HEAL"));            // legacy spelling
        assertTrue(SplashHealGuard.isHealingName("INSTANT_HEALTH"));  // 1.20.5 rename
        assertTrue(SplashHealGuard.isHealingName("REGENERATION"));    // stable
        assertTrue(SplashHealGuard.isHealingName("instant_health"));  // getKey()-style lowercase still matches
    }

    @Test
    void nonHealingAndAbsentNamesDoNotMatch() {
        assertFalse(SplashHealGuard.isHealingName("HARM"));
        assertFalse(SplashHealGuard.isHealingName("POISON"));
        assertFalse(SplashHealGuard.isHealingName("SPEED"));
        assertFalse(SplashHealGuard.isHealingName(null));
    }
}
