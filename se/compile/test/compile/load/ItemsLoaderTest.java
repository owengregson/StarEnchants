package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.DiagCode;

/** Loads the top-level {@code items/} config folder — parsing, defaults, and the diagnostic cases. */
class ItemsLoaderTest {

    @Test
    void absentFolderIsAnEmptyConfig() {
        ItemsConfig config = ItemsLoader.load(Path.of("/no/such/items/dir"));
        assertTrue(config.soulGem().isEmpty());
        assertFalse(config.hasErrors());
    }

    @Test
    void parsesAFullSoulGemConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("soul-gem.yml"), """
                type: soul-gem
                material: NETHER_STAR
                name: "&5Soul Gem"
                lore:
                  - "&7Souls: {AMOUNT}"
                  - "&7Right-click to toggle."
                souls-per-kill: 3
                sounds:
                  toggle-on:
                    - { sound: BLOCK_BEACON_POWER_SELECT, volume: 0.4, pitch: 2 }
                    - { sound: BLOCK_BONE_BLOCK_BREAK, volume: 1, pitch: 1 }
                  use:
                    - { sound: ENTITY_GENERIC_EAT, volume: 10.0, pitch: 1.0 }
                particles:
                  enable: { particle: REDSTONE, count: 20, color: { r: 91, g: 245, b: 83 }, spread: 1.25, y-offset: 1.0 }
                  idle: { particle: ENCHANTMENT_TABLE, count: 8, spread: 0.75, y-offset: 1.0 }
                """);

        ItemsConfig config = ItemsLoader.load(dir);

        assertFalse(config.hasErrors());
        SoulGemConfig gem = config.soulGem().orElseThrow();
        assertEquals("NETHER_STAR", gem.material());
        assertEquals("&5Soul Gem", gem.name());
        assertEquals(List.of("&7Souls: {AMOUNT}", "&7Right-click to toggle."), gem.lore());
        assertEquals(3, gem.soulsPerKill());

        // sounds round-trip the unified { sound, volume, pitch } bracket form, in order, as a list
        assertEquals(2, gem.sounds().toggleOn().size());
        assertEquals("BLOCK_BEACON_POWER_SELECT", gem.sounds().toggleOn().get(0).name());
        assertEquals(0.4f, gem.sounds().toggleOn().get(0).volume());
        assertEquals(2.0f, gem.sounds().toggleOn().get(0).pitch());
        assertEquals(10.0f, gem.sounds().use().get(0).volume());
        assertTrue(gem.sounds().combine().isEmpty()); // omitted action → silent

        // particles round-trip the unified { particle, count, color, spread, y-offset } bracket form
        SoulGemConfig.Particles p = gem.particles();
        assertEquals("REDSTONE", p.enable().type());
        assertEquals(91, p.enable().colorR());
        assertEquals(245, p.enable().colorG());
        assertEquals(83, p.enable().colorB());
        assertEquals(20, p.enable().amount());
        assertEquals(1.25, p.enable().spread());
        assertEquals(1.0, p.enable().yOffset());
        assertEquals("ENCHANTMENT_TABLE", p.idle().type());
        assertTrue(p.disable().isEmpty()); // omitted state → nothing spawned
        // soul-mode messages live in lang.yml (§L), not on the item config
    }

    @Test
    void omittedFieldsFallBackToDefaults(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("soul-gem.yml"), "type: soul-gem\n");

        SoulGemConfig gem = ItemsLoader.load(dir).soulGem().orElseThrow();
        assertEquals(SoulGemConfig.defaults().material(), gem.material());
        assertEquals(SoulGemConfig.defaults().soulsPerKill(), gem.soulsPerKill());
        assertEquals(SoulGemConfig.defaults().name(), gem.name());
    }

    @Test
    void parsesASlotsConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("slot-orb.yml"), """
                type: slot-orb
                orb-material: ENDER_PEARL
                orb-name: "&5Expander +{AMOUNT}"
                orb-lore:
                  - "&7+{AMOUNT} slots"
                orb-amount: 5
                hard-cap: 20
                """);

        SlotConfig slots = ItemsLoader.load(dir).slots().orElseThrow();
        assertEquals("ENDER_PEARL", slots.orbMaterial());
        assertEquals(5, slots.orbAmount());
        assertEquals(List.of("&7+{AMOUNT} slots"), slots.orbLore());
        assertEquals(20, slots.hardCap());
    }

    @Test
    void assemblesTheScrollFamilyFromPerItemFiles(@TempDir Path dir) throws Exception {
        // each scroll is its own file; ItemsLoader assembles one ScrollsConfig, defaulting absent members
        Files.writeString(dir.resolve("black-scroll.yml"), """
                type: black-scroll
                material: COAL
                name: "&8Void Scroll"
                min-convert: 40
                max-convert: 60
                """);
        Files.writeString(dir.resolve("randomizer-scroll.yml"), """
                type: randomizer-scroll
                material: GLOWSTONE_DUST
                min-percent: 10
                max-percent: 90
                """);

        ScrollsConfig scrolls = ItemsLoader.load(dir).scrolls().orElseThrow();
        assertEquals("COAL", scrolls.black().material());
        assertEquals("&8Void Scroll", scrolls.black().name());
        assertEquals(40, scrolls.black().minConvert());
        assertEquals(60, scrolls.black().maxConvert());
        assertEquals(ScrollsConfig.defaults().black().lore(), scrolls.black().lore());
        assertEquals("GLOWSTONE_DUST", scrolls.randomizer().material());
        assertEquals(10, scrolls.randomizer().minPercent());
        assertEquals(90, scrolls.randomizer().maxPercent());
        assertEquals(ScrollsConfig.defaults().randomizer().name(), scrolls.randomizer().name());
        // an entirely-absent member (holy white scroll) is filled from defaults
        assertEquals(ScrollsConfig.defaults().holy().name(), scrolls.holy().name());
    }

    @Test
    void parsesADustRangeAndWhiteScrollConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("dust.yml"), """
                type: dust
                material: REDSTONE
                name: "&cTinker Dust"
                min-bonus: 5
                max-bonus: 40
                """);
        Files.writeString(dir.resolve("white-scroll.yml"), """
                type: white-scroll
                material: MAP
                name: "&fWard"
                """);

        ItemsConfig config = ItemsLoader.load(dir);
        DustConfig dust = config.dust().orElseThrow();
        assertEquals("REDSTONE", dust.material());
        assertEquals(5, dust.minBonus());
        assertEquals(40, dust.maxBonus());
        assertEquals(DustConfig.defaults().sound(), dust.sound());
        WhiteScrollConfig white = config.whiteScroll().orElseThrow();
        assertEquals("MAP", white.material());
        assertEquals("&fWard", white.name());
    }

    @Test
    void dustSuccessBonusShorthandIsAFixedRange(@TempDir Path dir) throws Exception {
        // `success-bonus` shorthand pins a fixed dust: min == max
        Files.writeString(dir.resolve("dust.yml"), "type: dust\nsuccess-bonus: 25\n");

        DustConfig dust = ItemsLoader.load(dir).dust().orElseThrow();
        assertEquals(25, dust.minBonus());
        assertEquals(25, dust.maxBonus());
        assertEquals("25", dust.bonusLabel());
    }

    @Test
    void parsesAnUnopenedBookConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("unopened-book.yml"), """
                type: unopened-book
                material: KNOWLEDGE_BOOK
                name: "&d{TIER} crate"
                min-success: 40
                max-success: 60
                """);

        UnopenedBookConfig book = ItemsLoader.load(dir).unopenedBook().orElseThrow();
        assertEquals("KNOWLEDGE_BOOK", book.material());
        assertEquals("&d{TIER} crate", book.name());
        assertEquals(40, book.minSuccess());
        assertEquals(60, book.maxSuccess());
    }

    @Test
    void parsesAnEnchantBookConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("enchant-book.yml"), """
                type: enchant-book
                material: BOOK
                name: "&b{ENCHANT} {LEVEL}"
                destroy-on-fail: false
                """);

        EnchantBookConfig book = ItemsLoader.load(dir).enchantBook().orElseThrow();
        assertEquals("BOOK", book.material());
        assertEquals("&b{ENCHANT} {LEVEL}", book.name());
        assertFalse(book.destroyOnFail());
        // an omitted list field falls back to the built-in placeholder spec
        assertEquals(EnchantBookConfig.defaults().lore(), book.lore());
    }

    @Test
    void aSecondConfigOfTheSameTypeWarnsAndKeepsTheFirst(@TempDir Path dir) throws Exception {
        // two files declare the same type; files load in sorted-name order, so the first wins and the second
        // is a non-blocking W_ITEM_DUP warning. This skip-second branch repeats for every item type but was
        // never exercised once.
        Files.writeString(dir.resolve("a-white.yml"), "type: white-scroll\nname: \"&fFirst\"\n");
        Files.writeString(dir.resolve("b-white.yml"), "type: white-scroll\nname: \"&fSecond\"\n");

        ItemsConfig config = ItemsLoader.load(dir);
        assertFalse(config.hasErrors());
        assertEquals("&fFirst", config.whiteScroll().orElseThrow().name());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.is(DiagCode.W_ITEM_DUP)));
    }

    @Test
    void aMissingTypeIsABlockingError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("mystery.yml"), "material: STONE\n");

        ItemsConfig config = ItemsLoader.load(dir);
        assertTrue(config.soulGem().isEmpty());
        assertTrue(config.hasErrors());
    }

    @Test
    void anUnknownTypeWarnsButDoesNotError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("future.yml"), "type: time-machine\n");

        ItemsConfig config = ItemsLoader.load(dir);
        assertFalse(config.hasErrors()); // warn, don't block — stays forward-compatible
        assertFalse(config.diagnostics().isEmpty());
    }

    @Test
    void anInvalidNumberWarnsAndUsesTheDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("soul-gem.yml"), "type: soul-gem\nsouls-per-kill: lots\n");

        ItemsConfig config = ItemsLoader.load(dir);
        SoulGemConfig gem = config.soulGem().orElseThrow();
        assertEquals(SoulGemConfig.defaults().soulsPerKill(), gem.soulsPerKill());
        assertFalse(config.hasErrors());
        assertFalse(config.diagnostics().isEmpty());
    }
}
