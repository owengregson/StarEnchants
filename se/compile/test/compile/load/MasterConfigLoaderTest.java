package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Loads the master {@code config.yml} (§L) — defaults, full parse, omitted-section fallback, diagnostics. */
class MasterConfigLoaderTest {

    @Test
    void absentFileIsAllDefaults() {
        MasterConfig config = MasterConfigLoader.load(Path.of("/no/such/config.yml"));
        assertFalse(config.hasErrors());
        assertEquals(9, config.slots().base());
        assertEquals(1, config.crystals().slots());
        assertEquals(16, config.crystals().maxStack());
        assertEquals(4.0, config.heroic().maxOutgoingFactor());
        assertTrue(config.souls().depositOnAnyKill());
        assertTrue(config.integrations().protection());
        assertTrue(config.integrations().economy());
        assertTrue(config.reload().reResolvePlayers());
        assertEquals(0, config.reload().autoSeconds());
        assertTrue(config.lore().roman());
        assertEquals("&7", config.lore().enchantColor());
        assertTrue(config.commandTrigger().enabled());          // §B COMMAND trigger command on by default
        assertEquals("cast", config.commandTrigger().name());
    }

    @Test
    void parsesEverySection(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                slots:
                  base: 12
                souls:
                  deposit-on-any-kill: false
                crystals:
                  slots: 3
                  max-stack: 8
                heroic:
                  max-outgoing-factor: 2.5
                lore:
                  enchant-color: "&a"
                  level-color: "&e"
                  crystal-color: "&d"
                  roman: false
                  unknown-label: "&8?"
                integrations:
                  protection: false
                  economy: false
                  named:
                    worldguard: false
                    vault: true
                reload:
                  re-resolve-players: false
                  auto-seconds: 600
                command-trigger:
                  enabled: false
                  name: ability
                  description: "Cast it."
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors());
        assertEquals(12, config.slots().base());
        assertFalse(config.souls().depositOnAnyKill());
        assertEquals(3, config.crystals().slots());
        assertEquals(8, config.crystals().maxStack());
        assertEquals(2.5, config.heroic().maxOutgoingFactor());
        assertEquals("&a", config.lore().enchantColor());
        assertEquals("&e", config.lore().levelColor());
        assertEquals("&d", config.lore().crystalColor());
        assertFalse(config.lore().roman());
        assertEquals("&8?", config.lore().unknownLabel());
        assertFalse(config.integrations().protection());
        assertFalse(config.integrations().economy());
        assertFalse(config.integrations().enabled("worldguard")); // listed false
        assertTrue(config.integrations().enabled("vault"));        // listed true
        assertTrue(config.integrations().enabled("oraxen"));       // unlisted ⇒ enabled
        assertFalse(config.reload().reResolvePlayers());
        assertEquals(600, config.reload().autoSeconds());
        assertFalse(config.commandTrigger().enabled());
        assertEquals("ability", config.commandTrigger().name());
        assertEquals("Cast it.", config.commandTrigger().description());
    }

    @Test
    void omittedSectionsFallBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                slots:
                  base: 7
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors());
        assertEquals(7, config.slots().base());                 // the one set value
        assertEquals(1, config.crystals().slots());             // every other section is its default
        assertEquals(4.0, config.heroic().maxOutgoingFactor());
        assertTrue(config.integrations().economy());
    }

    @Test
    void invalidNumberWarnsAndKeepsDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                slots:
                  base: not-a-number
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors()); // a bad number is a WARNING, not a blocking error
        assertEquals(9, config.slots().base());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.code().equals("W_CONFIG_NUM")));
    }

    @Test
    void clampsOutOfRangeValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                slots:
                  base: -5
                heroic:
                  max-outgoing-factor: 0.2
                crystals:
                  max-stack: 0
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertEquals(0, config.slots().base());              // clamped to ≥ 0
        assertEquals(1.0, config.heroic().maxOutgoingFactor()); // clamped to ≥ 1.0
        assertEquals(1, config.crystals().maxStack());       // clamped to ≥ 1
    }
}
