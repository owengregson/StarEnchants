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
        assertEquals(defaults.mining(), config.mining());
        assertEquals(defaults.messages(), config.messages());
        assertEquals(defaults.books(), config.books());
        assertEquals(defaults.slots(), config.slots());
        assertEquals(defaults.souls(), config.souls());
        assertEquals(defaults.crystals(), config.crystals());
        assertEquals(defaults.lore(), config.lore());
        assertEquals(defaults.integrations(), config.integrations());
        assertEquals(defaults.reload(), config.reload());
        assertEquals(defaults.commandTrigger(), config.commandTrigger());
        assertEquals(defaults.messageOnActivate(), config.messageOnActivate());
        assertEquals(defaults.sets(), config.sets());
        assertEquals(defaults.stations(), config.stations());
        assertEquals(defaults.applyCues(), config.applyCues());
    }

    @Test
    void stationGuardsDefaultOnAndParseExplicitFalse(@TempDir Path dir) throws Exception {
        MasterConfig.StationsSection d = MasterConfig.StationsSection.defaults();
        assertTrue(d.anvilGuard() && d.grindstoneGuard() && d.smithingGuard(), "all station guards on by default");

        Path file = dir.resolve("stations.yml");
        Files.writeString(file, """
                stations:
                  anvil-guard: false
                  grindstone-guard: false
                  smithing-guard: true
                """);
        MasterConfig.StationsSection s = MasterConfigLoader.load(file).stations();
        assertFalse(s.anvilGuard());
        assertFalse(s.grindstoneGuard());
        assertTrue(s.smithingGuard()); // explicit true, and an absent section would also default true

        // a garbage value and an absent section both fall back to the on default
        Path garbage = dir.resolve("garbage-stations.yml");
        Files.writeString(garbage, """
                stations:
                  anvil-guard: maybe
                """);
        assertTrue(MasterConfigLoader.load(garbage).stations().anvilGuard());

        Path absent = dir.resolve("absent-stations.yml");
        Files.writeString(absent, """
                combat:
                  pvp: false
                """);
        assertTrue(MasterConfigLoader.load(absent).stations().smithingGuard());
    }

    @Test
    void parsesTheUniversalSetEffects(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                sets:
                  message-uppercase: true
                  use-set-color: false
                  equip-sound:
                    - { sound: BLOCK_BEACON_POWER_SELECT, volume: 0.4, pitch: 2 }
                    - { sound: ITEM_ARMOR_EQUIP_COPPER, volume: 0.4, pitch: 1 }
                  unequip-sound:
                    - { sound: BLOCK_GLASS_BREAK, volume: 0.25, pitch: 0.8 }
                  equip-particle: { particle: REDSTONE, count: 20, color: { r: 12, g: 34, b: 56 }, spread: 1.25, y-offset: 1.0 }
                """);

        MasterConfig.SetsSection sets = MasterConfigLoader.load(file).sets();

        assertTrue(sets.messageUppercase());
        assertFalse(sets.useSetColor());
        assertEquals(2, sets.equipSound().size());
        assertEquals("BLOCK_BEACON_POWER_SELECT", sets.equipSound().get(0).name());
        assertEquals(2.0f, sets.equipSound().get(0).pitch());
        assertEquals(1, sets.unequipSound().size());
        assertEquals("REDSTONE", sets.equipParticle().type());
        assertEquals(12, sets.equipParticle().colorR());
        assertEquals(20, sets.equipParticle().amount());
        assertTrue(sets.unequipParticle().isEmpty()); // omitted → nothing spawned
    }

    @Test
    void parsesApplyCues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                apply-cues:
                  success:
                    sound: { sound: ENTITY_PLAYER_LEVELUP, volume: 0.5, pitch: 2.0 }
                    particles:
                      - HAPPY_VILLAGER
                      - TOTEM
                  fail:
                    sound: ENTITY_ANVIL_LAND
                """);

        MasterConfig.ApplyCuesSection cues = MasterConfigLoader.load(file).applyCues();

        assertEquals("ENTITY_PLAYER_LEVELUP", cues.successSound().name());
        assertEquals(0.5f, cues.successSound().volume());
        assertEquals(2.0f, cues.successSound().pitch());
        assertEquals(java.util.List.of("HAPPY_VILLAGER", "TOTEM"), cues.successParticles());
        // a bare-string sound parses at 1.0/1.0; an omitted particles list falls back to the default
        assertEquals("ENTITY_ANVIL_LAND", cues.failSound().name());
        assertEquals(1.0f, cues.failSound().pitch());
        assertEquals(MasterConfig.ApplyCuesSection.defaults().failParticles(), cues.failParticles());
    }

    @Test
    void parsesMessageOnActivate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                message-on-activate:
                  by-enabled: true
                  by-template: "by {ENCHANT} {VICTIM}"
                  on-enabled: true
                  uppercase: true
                """);

        MasterConfig.MessageOnActivateSection moa = MasterConfigLoader.load(file).messageOnActivate();

        assertTrue(moa.byEnabled());
        assertEquals("by {ENCHANT} {VICTIM}", moa.byTemplate());
        assertTrue(moa.onEnabled());
        assertTrue(moa.uppercase());
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
                  attack-scale: 5.0
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
        assertEquals(5.0, config.combat().attackScale());
        assertFalse(config.combat().pvp());
        assertTrue(config.combat().pve());
        assertEquals("&8[&dSE&8] ", config.messages().prefix());
        assertFalse(config.messages().feedback());
    }

    @Test
    void parsesTheMasksSectionAndFeatureFlag(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                features:
                  masks: false
                masks:
                  near-commands:
                    - near
                    - whereis
                  near-radius: 500
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors());
        assertFalse(config.features().masks());
        assertTrue(config.features().pets()); // an omitted feature stays on
        assertEquals(java.util.List.of("near", "whereis"), config.masks().nearCommands());
        assertEquals(500, config.masks().nearRadius());
    }

    @Test
    void masksNearCommandsEmptyListDisablesTheInterceptionButAbsentDefaults(@TempDir Path dir) throws Exception {
        // present-but-empty near-commands is honoured (disables the /near interception), not defaulted to [near]
        Path empty = dir.resolve("empty.yml");
        Files.writeString(empty, """
                masks:
                  near-commands: []
                """);
        assertTrue(MasterConfigLoader.load(empty).masks().nearCommands().isEmpty());

        // an absent masks section defaults near-commands to [near] and the radius to 200
        Path absent = dir.resolve("absent.yml");
        Files.writeString(absent, """
                combat:
                  pvp: false
                """);
        MasterConfig.MasksSection d = MasterConfigLoader.load(absent).masks();
        assertEquals(java.util.List.of("near"), d.nearCommands());
        assertEquals(200, d.nearRadius());
    }

    @Test
    void miningGuardDefaultsOnAndParsesExplicitFalse(@TempDir Path dir) throws Exception {
        assertTrue(MasterConfig.MiningSection.defaults().placedBlockGuard(), "guard is on by default");

        Path off = dir.resolve("off.yml");
        Files.writeString(off, """
                mining:
                  placed-block-guard: false
                """);
        assertFalse(MasterConfigLoader.load(off).mining().placedBlockGuard());

        // an absent key, and a garbage value, both fall back to the on default
        Path garbage = dir.resolve("garbage.yml");
        Files.writeString(garbage, """
                mining:
                  placed-block-guard: maybe
                """);
        assertTrue(MasterConfigLoader.load(garbage).mining().placedBlockGuard());

        Path absent = dir.resolve("absent.yml");
        Files.writeString(absent, """
                combat:
                  pvp: false
                """);
        assertTrue(MasterConfigLoader.load(absent).mining().placedBlockGuard());
        // an absent engine section defaults the offline-state and temp-block sweep periods
        assertEquals(6000, MasterConfigLoader.load(absent).engine().offlineStateSweepTicks());
        assertEquals(600, MasterConfigLoader.load(absent).engine().tempBlockSweepTicks());
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
                  max-merge: 8
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
                engine:
                  offline-state-sweep-ticks: 1200
                  temp-block-sweep-ticks: 300
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors());
        assertEquals(1200, config.engine().offlineStateSweepTicks());
        assertEquals(300, config.engine().tempBlockSweepTicks());
        assertEquals(12, config.slots().base());
        assertFalse(config.souls().depositOnAnyKill());
        assertEquals(3, config.crystals().slots());
        assertEquals(8, config.crystals().maxMerge());
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
    void invalidBooleanWarnsAndKeepsDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                combat:
                  pvp: maybe
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertFalse(config.hasErrors()); // a non-canonical boolean warns, never blocks
        assertEquals(MasterConfig.CombatSection.defaults().pvp(), config.combat().pvp());
        assertTrue(config.diagnostics().stream().anyMatch(d -> d.is(DiagCode.W_CONFIG_BOOL)));
    }

    @Test
    void clampsOutOfRangeValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                slots:
                  base: -5
                crystals:
                  max-merge: 0
                """);

        MasterConfig config = MasterConfigLoader.load(file);

        assertEquals(0, config.slots().base());              // clamped to ≥ 0
        assertEquals(1, config.crystals().maxMerge());       // clamped to ≥ 1
    }
}
