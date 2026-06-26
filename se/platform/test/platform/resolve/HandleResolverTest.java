package platform.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.spec.HandleCategory;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HandleResolverTest {

    private static final Map<String, String> POTIONS = Aliases.forCategory(HandleCategory.POTION_EFFECT);
    private static final Map<String, String> SOUNDS = Aliases.forCategory(HandleCategory.SOUND);

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

    @Test
    void resolvesModernSoundsBackwardToTheir18NamesOnAnOlderServer() {
        // The shipped content names the modern (1.13-flattened) sounds; on a 1.8 server only the old enum
        // constants exist, so the resolver must intern those — the fix for 42 of the 45 legacy boot diagnostics.
        assertEquals("EXPLODE",
                HandleResolver.resolve("ENTITY_GENERIC_EXPLODE", SOUNDS, Set.of("EXPLODE")::contains).orElseThrow());
        assertEquals("LEVEL_UP",
                HandleResolver.resolve("ENTITY_PLAYER_LEVELUP", SOUNDS, Set.of("LEVEL_UP")::contains).orElseThrow());
        assertEquals("ANVIL_LAND",
                HandleResolver.resolve("BLOCK_ANVIL_LAND", SOUNDS, Set.of("ANVIL_LAND")::contains).orElseThrow());
        assertEquals("WITHER_SPAWN",
                HandleResolver.resolve("ENTITY_WITHER_SPAWN", SOUNDS, Set.of("WITHER_SPAWN")::contains).orElseThrow());
    }
}
