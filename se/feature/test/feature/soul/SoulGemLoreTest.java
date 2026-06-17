package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.load.SoulGemConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for soul-gem lore rendering — no Bukkit. Pins the {@code {AMOUNT}} / {@code {SOUL-COLOR}}
 * placeholder substitution (lore is rendered from state, never parsed back) and the count-tiered
 * soul-colour thresholds. Returned lines stay {@code &}-coded — the {@code ItemFactory} colours them.
 */
final class SoulGemLoreTest {

    private static SoulGemConfig cfg(List<String> lore) {
        return new SoulGemConfig("EMERALD", "&aSoul Gem", lore, 1, "", "", "");
    }

    @Test
    void substitutesAmountAndColor() {
        List<String> lore = SoulService.renderGemLore(
                cfg(List.of("&7Souls: {SOUL-COLOR}{AMOUNT}", "&7Right-click to toggle.")), 100);
        assertEquals("&7Souls: &a100", lore.get(0)); // 100 → green tier
        assertEquals("&7Right-click to toggle.", lore.get(1)); // no placeholders → verbatim
    }

    @Test
    void amountOfZero() {
        List<String> lore = SoulService.renderGemLore(cfg(List.of("Souls={AMOUNT}")), 0);
        assertEquals("Souls=0", lore.get(0));
    }

    @Test
    void colorTiers() {
        assertEquals("&7", SoulService.soulColor(0));
        assertEquals("&f", SoulService.soulColor(1));
        assertEquals("&f", SoulService.soulColor(63));
        assertEquals("&a", SoulService.soulColor(64));
        assertEquals("&a", SoulService.soulColor(255));
        assertEquals("&b", SoulService.soulColor(256));
        assertEquals("&b", SoulService.soulColor(1023));
        assertEquals("&d", SoulService.soulColor(1024));
    }
}
