package compile.load;

import schema.diag.DiagCode;

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
        MasterConfig defaults = MasterConfig.defaults();
        assertFalse(config.hasErrors());
        // An absent file yields EXACTLY the defaults — compare each section to the default source (records,
        // so value-equal) instead of re-typing two dozen default values that would couple this test to every
        // retune. A regression where the absent path diverges from defaults() for any section still fails here.
        assertEquals(defaults.features(), config.features());
        assertEquals(defaults.combat(), config.combat());
        assertEquals(defaults.messages(), config.messages());
        assertEquals(defaults.books(), config.books());
        assertEquals(defaults.slots(), config.slots());
        assertEquals(defaults.souls(), config.souls());
        assertEquals(defaults.crystals(), config.crystals());
        assertEquals(defaults.heroic(), config.heroic());
        assertEquals(defaults.lore(), config.lore());
        assertEquals(defaults.integrations(), config.integrations());
        assertEquals(defaults.reload(), config.reload());
        assertEquals(defaults.commandTrigger(), config.commandTrigger());
        assertEquals(defaults.messageOnActivate(), config.messageOnActivate());
    }

    @Test
    void parsesMessageOnActivate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                message-on-activate:
                  by-enabled: true
                  by-template: "by {ENCHANT} {VICTIM}"
                  on-enabled: true
                """);

        MasterConfig.MessageOnActivateSection moa = MasterConfigLoader.load(file).messageOnActivate();

        assertTrue(moa.byEnabled());
        assertEquals("by {ENCHANT} {VICTIM}", moa.byTemplate());
        assertTrue(moa.onEnabled());
        // an omitted template falls back to the default rather than blanking
        assertEquals(MasterConfig.MessageOnActivateSection.defaults().onTemplate(), moa.onTemplate());
    }

    @Test
    void parsesFeaturesCombatAndMessages(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                features:
                  enchants: false
                  crystals: false
                  souls: false
                combat:
                  max-bonus-damage: 5.0
                  max-bonus-reduction: 0.8
                  pvp: false
                  pve: true
                messages:
                  prefix: "&8[&dSE&8] "
                  feedback: false
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors());
        assertFalse(config.features().enchants());
        assertFalse(config.features().crystals());
        assertFalse(config.features().souls());
        assertTrue(config.features().sets()); // omitted feature stays on
        assertTrue(config.features().heroic());
        assertEquals(5.0, config.combat().maxBonusDamage());
        assertEquals(0.8, config.combat().maxBonusReduction());
        assertFalse(config.combat().pvp());
        assertTrue(config.combat().pve());
        assertEquals("&8[&dSE&8] ", config.messages().prefix());
        assertFalse(config.messages().feedback());
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
        assertFalse(config.integrations().enabled("worldguard"));
        assertTrue(config.integrations().enabled("vault"));
        assertTrue(config.integrations().enabled("oraxen")); // unlisted integration defaults enabled
        assertFalse(config.reload().reResolvePlayers());
        assertEquals(600, config.reload().autoSeconds());
        assertFalse(config.commandTrigger().enabled());
        assertEquals("ability", config.commandTrigger().name());
        assertEquals("Cast it.", config.commandTrigger().description());
    }

    @Test
    void blankLevelColorIsPreservedButAnAbsentKeyDefaults(@TempDir Path dir) throws Exception {
        // A present-but-empty level-color is meaningful (the level inherits the tier colour), so it must NOT
        // be coerced to the default the way a blank enchant-color would be.
        Path blank = dir.resolve("blank.yml");
        Files.writeString(blank, """
                lore:
                  level-color: ""
                """);
        assertEquals("", MasterConfigLoader.load(blank).lore().levelColor());

        // An ABSENT level-color still falls back to the default.
        Path absent = dir.resolve("absent.yml");
        Files.writeString(absent, """
                lore:
                  enchant-color: "&3"
                """);
        assertEquals("&f", MasterConfigLoader.load(absent).lore().levelColor());
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
        assertEquals(7, config.slots().base());
        assertEquals(1, config.crystals().slots());
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

        assertFalse(config.hasErrors()); // a bad number warns, never blocks
        assertEquals(9, config.slots().base());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.is(DiagCode.W_CONFIG_NUM)));
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
