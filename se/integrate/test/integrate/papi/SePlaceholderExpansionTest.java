package integrate.papi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code %starenchants_…%} placeholder lookup of {@link SePlaceholderExpansion}: soul mode renders
 * on/off, souls renders the accessor value, an offline player reads the defaults, and an unknown token returns
 * null (PAPI leaves it raw). Registration against a live PAPI is verified out-of-matrix (docs/decisions/0027).
 */
class SePlaceholderExpansionTest {

    private final Player player = mock(Player.class);

    @Test
    void soulModeRendersOnOff() {
        assertEquals("on", SePlaceholderExpansion.resolve("soulmode", player, p -> true, p -> 0));
        assertEquals("off", SePlaceholderExpansion.resolve("soulmode", player, p -> false, p -> 0));
    }

    @Test
    void soulsRendersAccessorValue() {
        assertEquals("42", SePlaceholderExpansion.resolve("souls", player, p -> true, p -> 42));
    }

    @Test
    void caseInsensitive() {
        assertEquals("on", SePlaceholderExpansion.resolve("SoulMode", player, p -> true, p -> 0));
    }

    @Test
    void offlinePlayerReadsDefaults() {
        assertEquals("off", SePlaceholderExpansion.resolve("soulmode", null, p -> true, p -> 99));
        assertEquals("0", SePlaceholderExpansion.resolve("souls", null, p -> true, p -> 99));
    }

    @Test
    void unknownPlaceholderReturnsNull() {
        assertNull(SePlaceholderExpansion.resolve("nope", player, p -> true, p -> 0));
    }

    @Test
    void nullParamsRendersEmpty() {
        assertEquals("", SePlaceholderExpansion.resolve(null, player, p -> true, p -> 0));
    }
}
