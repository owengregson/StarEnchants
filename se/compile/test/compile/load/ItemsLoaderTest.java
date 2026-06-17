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
        assertEquals("on", gem.messageActivate());
        assertEquals("left {AMOUNT}", gem.messageSoulUse());
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
        Files.writeString(dir.resolve("slots.yml"), """
                type: slots
                orb-material: ENDER_PEARL
                orb-name: "&5Expander +{AMOUNT}"
                orb-lore:
                  - "&7+{AMOUNT} slots"
                orb-amount: 5
                gem-material: QUARTZ
                gem-name: "&dGem"
                hard-cap: 20
                message-apply: "now {SLOTS}"
                message-at-cap: "maxed"
                """);

        SlotConfig slots = ItemsLoader.load(dir).slots().orElseThrow();
        assertEquals("ENDER_PEARL", slots.orbMaterial());
        assertEquals(5, slots.orbAmount());
        assertEquals(List.of("&7+{AMOUNT} slots"), slots.orbLore());
        assertEquals("QUARTZ", slots.gemMaterial());
        assertEquals(20, slots.hardCap());
        assertEquals("now {SLOTS}", slots.messageApply());
        // Omitted gem-lore falls back to the default.
        assertEquals(SlotConfig.defaults().gemLore(), slots.gemLore());
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
        Files.writeString(dir.resolve("future.yml"), "type: black-scroll\n"); // not yet supported

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
