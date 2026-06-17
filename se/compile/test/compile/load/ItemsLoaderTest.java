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
