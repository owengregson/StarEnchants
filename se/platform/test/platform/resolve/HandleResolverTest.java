package platform.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.spec.HandleCategory;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HandleResolverTest {

    private static final Map<String, String> POTIONS = Aliases.forCategory(HandleCategory.POTION_EFFECT);

    @Test
    void resolvesAModernTokenThatExistsDirectly() {
        assertEquals("NAUSEA",
                HandleResolver.resolve("NAUSEA", POTIONS, Set.of("NAUSEA")::contains).orElseThrow());
    }

    @Test
    void resolvesALegacyTokenForwardToItsModernName() {
        // CONFUSION → NAUSEA on a modern server (only NAUSEA exists).
        assertEquals("NAUSEA",
                HandleResolver.resolve("CONFUSION", POTIONS, Set.of("NAUSEA")::contains).orElseThrow());
    }

    @Test
    void resolvesAModernTokenBackwardToItsLegacyNameOnAnOlderServer() {
        // NAUSEA → CONFUSION on a pre-rename server (only CONFUSION exists).
        assertEquals("CONFUSION",
                HandleResolver.resolve("NAUSEA", POTIONS, Set.of("CONFUSION")::contains).orElseThrow());
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("NAUSEA",
                HandleResolver.resolve("confusion", POTIONS, Set.of("NAUSEA")::contains).orElseThrow());
    }

    @Test
    void prefersADirectMatchOverAnAlias() {
        // Both forms exist → the token itself wins (no needless aliasing).
        assertEquals("HEAL",
                HandleResolver.resolve("HEAL", POTIONS, Set.of("HEAL", "INSTANT_HEALTH")::contains).orElseThrow());
    }

    @Test
    void unknownTokenResolvesToEmpty() {
        assertTrue(HandleResolver.resolve("FLIBBERTIGIBBET", POTIONS, Set.of("NAUSEA")::contains).isEmpty());
    }
}
