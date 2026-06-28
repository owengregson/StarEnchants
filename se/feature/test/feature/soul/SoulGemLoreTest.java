package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.load.SoulGemConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for soul-gem lore rendering + the configurable economy knobs — no Bukkit. Pins the
 * {@code {AMOUNT}} / {@code {SOUL-COLOR}} placeholder substitution (lore is rendered from state, never
 * parsed back), the now-CONFIGURABLE soul-colour tiers ({@link SoulGemConfig#colorFor}, a Cosmic Enchants-style plugin hard-coded
 * these), and the per-mob deposit resolution ({@link SoulGemConfig#soulsFor}). Returned lore lines stay
 * {@code &}-coded — the {@code ItemFactory} colours them.
 */
final class SoulGemLoreTest {

    private static SoulGemConfig withLore(List<String> lore) {
        SoulGemConfig d = SoulGemConfig.defaults();
        return new SoulGemConfig(d.material(), d.name(), lore, d.soulsPerKill(), d.soulsPerMob(),
                d.colorTiers(), d.emptyColor(), d.sounds(), d.soundActivate(), d.soundDeactivate(),
                d.soundCombine(), d.particlesActive(), d.particlesActivate(), d.particlesDeactivate());
    }

    @Test
    void substitutesAmountAndColor() {
        List<String> lore = SoulService.renderGemLore(
                withLore(List.of("&7Souls: {SOUL-COLOR}{AMOUNT}", "&7Right-click to toggle.")), 100);
        assertEquals("&7Souls: &a100", lore.get(0)); // 100 → green tier (default tiers)
        assertEquals("&7Right-click to toggle.", lore.get(1)); // no placeholders → verbatim
    }

    @Test
    void amountOfZero() {
        List<String> lore = SoulService.renderGemLore(withLore(List.of("Souls={AMOUNT}")), 0);
        assertEquals("Souls=0", lore.get(0));
    }

    @Test
    void parenAliasesAreEquivalentToBraceForms() {
        List<String> lore = SoulService.renderGemLore(
                withLore(List.of("&7Souls: (soul_amt_color)(soul_amt)")), 100);
        assertEquals("&7Souls: &a100", lore.get(0)); // (soul_amt)/(soul_amt_color) == {AMOUNT}/{SOUL-COLOR}
    }

    @Test
    void renderGemNameCarriesTheLiveCount() {
        SoulGemConfig cfg = withName("&c&lSoul Gem [&r(soul_amt_color)&n&l(soul_amt)&r&c&l]");
        assertEquals("&c&lSoul Gem [&r&a&n&l100&r&c&l]", SoulService.renderGemName(cfg, 100));
        assertEquals("&c&lSoul Gem [&r&7&n&l0&r&c&l]", SoulService.renderGemName(cfg, 0)); // empty → empty colour
    }

    private static SoulGemConfig withName(String name) {
        SoulGemConfig d = SoulGemConfig.defaults();
        return new SoulGemConfig(d.material(), name, d.lore(), d.soulsPerKill(), d.soulsPerMob(),
                d.colorTiers(), d.emptyColor(), d.sounds(), d.soundActivate(), d.soundDeactivate(),
                d.soundCombine(), d.particlesActive(), d.particlesActivate(), d.particlesDeactivate());
    }

    @Test
    void defaultColorTiers() {
        SoulGemConfig cfg = SoulGemConfig.defaults();
        assertEquals("&7", cfg.colorFor(0));
        assertEquals("&f", cfg.colorFor(1));
        assertEquals("&f", cfg.colorFor(63));
        assertEquals("&a", cfg.colorFor(64));
        assertEquals("&a", cfg.colorFor(255));
        assertEquals("&b", cfg.colorFor(256));
        assertEquals("&b", cfg.colorFor(1023));
        assertEquals("&d", cfg.colorFor(1024));
    }

    @Test
    void customColorTiersAreHonoured() {
        SoulGemConfig cfg = new SoulGemConfig("EMERALD", "&aSoul Gem", List.of("{SOUL-COLOR}{AMOUNT}"), 1,
                Map.of(), List.of(new SoulGemConfig.ColorTier(10, "&c"), new SoulGemConfig.ColorTier(50, "&6")),
                "&8", true, "a", "b", "c", List.of(), List.of(), List.of());
        assertEquals("&8", cfg.colorFor(0)); // below every tier → emptyColor
        assertEquals("&8", cfg.colorFor(9));
        assertEquals("&c", cfg.colorFor(10)); // first tier
        assertEquals("&c", cfg.colorFor(49));
        assertEquals("&6", cfg.colorFor(50)); // higher tier wins (tiers sorted highest-first internally)
        assertEquals("&6", cfg.colorFor(9999));
    }

    @Test
    void soulsPerMobOverridesFlatAmount() {
        SoulGemConfig cfg = new SoulGemConfig("EMERALD", "g", List.of(), 2,
                Map.of("wither", 100, "ZOMBIE", 5), List.of(new SoulGemConfig.ColorTier(1, "&f")), "&7",
                true, "a", "b", "c", List.of(), List.of(), List.of());
        assertEquals(100, cfg.soulsFor("WITHER")); // case-insensitive key match
        assertEquals(5, cfg.soulsFor("zombie"));
        assertEquals(2, cfg.soulsFor("CREEPER")); // no entry → flat per-kill
        assertEquals(2, cfg.soulsFor(null));
    }
}
