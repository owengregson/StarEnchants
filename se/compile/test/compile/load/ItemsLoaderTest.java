package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                message-activate: "on"
                message-deactivate: "off"
                message-soul-use: "left {AMOUNT}"
                """);

        ItemsConfig config = ItemsLoader.load(dir);

        assertFalse(config.hasErrors());
        SoulGemConfig gem = config.soulGem().orElseThrow();
        assertEquals("NETHER_STAR", gem.material());
        assertEquals("&5Soul Gem", gem.name());
        assertEquals(List.of("&7Souls: {AMOUNT}", "&7Right-click to toggle."), gem.lore());
        assertEquals(3, gem.soulsPerKill());
        // (soul-mode messages moved to lang.yml in §L — no longer on the item config)
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
        // The scroll family is now one physical item per file; ItemsLoader assembles them into one
        // ScrollsConfig, filling any absent member with its default.
        Files.writeString(dir.resolve("black-scroll.yml"), """
                type: black-scroll
                material: COAL
                name: "&8Void Scroll"
                success-chance: 50
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
        assertEquals(50, scrolls.black().successChance());
        // Omitted black lore falls back to the default.
        assertEquals(ScrollsConfig.defaults().black().lore(), scrolls.black().lore());
        assertEquals("GLOWSTONE_DUST", scrolls.randomizer().material());
        assertEquals(10, scrolls.randomizer().minPercent());
        assertEquals(90, scrolls.randomizer().maxPercent());
        // Omitted randomizer name falls back to the default.
        assertEquals(ScrollsConfig.defaults().randomizer().name(), scrolls.randomizer().name());
        // An entirely-absent member (the holy white scroll) is filled from defaults.
        assertEquals(ScrollsConfig.defaults().holy().name(), scrolls.holy().name());
    }

    @Test
    void parsesADustAndWhiteScrollConfig(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("dust.yml"), """
                type: dust
                material: REDSTONE
                name: "&cTinker Dust"
                success-bonus: 25
                """);
        Files.writeString(dir.resolve("white-scroll.yml"), """
                type: white-scroll
                material: MAP
                name: "&fWard"
                """);

        ItemsConfig config = ItemsLoader.load(dir);
        DustConfig dust = config.dust().orElseThrow();
        assertEquals("REDSTONE", dust.material());
        assertEquals(25, dust.successBonus());
        // Omitted sound falls back to the default.
        assertEquals(DustConfig.defaults().sound(), dust.sound());
        WhiteScrollConfig white = config.whiteScroll().orElseThrow();
        assertEquals("MAP", white.material());
        assertEquals("&fWard", white.name());
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
    void aMissingTypeIsABlockingError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("mystery.yml"), "material: STONE\n");

        ItemsConfig config = ItemsLoader.load(dir);
        assertTrue(config.soulGem().isEmpty());
        assertTrue(config.hasErrors());
    }

    @Test
    void anUnknownTypeWarnsButDoesNotError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("future.yml"), "type: time-machine\n"); // not a recognised item type

        ItemsConfig config = ItemsLoader.load(dir);
        assertFalse(config.hasErrors()); // a warning, not blocking — forward-compatible
        assertFalse(config.diagnostics().isEmpty());
    }

    @Test
    void anInvalidNumberWarnsAndUsesTheDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("soul-gem.yml"), "type: soul-gem\nsouls-per-kill: lots\n");

        ItemsConfig config = ItemsLoader.load(dir);
        SoulGemConfig gem = config.soulGem().orElseThrow();
        assertEquals(SoulGemConfig.defaults().soulsPerKill(), gem.soulsPerKill());
        assertFalse(config.hasErrors()); // warning only
        assertFalse(config.diagnostics().isEmpty());
    }
}
