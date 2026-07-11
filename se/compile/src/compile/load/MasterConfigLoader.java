package compile.load;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Severity;
import schema.diag.Source;

/**
 * Loads the master {@code config.yml} into an immutable {@link MasterConfig} — each top-level section read by
 * a {@code read*} helper, falling back per-field to the section default. Reuses the content diagnostics so
 * {@code /se reload --dry-run} surfaces config faults. Never throws: absent/unreadable/malformed yields defaults.
 */
public final class MasterConfigLoader {

    private MasterConfigLoader() {
    }

    public static MasterConfig load(Path configFile) {
        Diagnostics diags = new Diagnostics();
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return MasterConfig.defaults();
        }
        String yaml;
        try {
            yaml = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diags.error(DiagCode.E_CONFIG_IO, "could not read config.yml: " + e.getMessage(), Source.ofFile("config.yml"));
            return new MasterConfig(MasterConfig.FeaturesSection.defaults(), MasterConfig.CombatSection.defaults(),
                    MasterConfig.MiningSection.defaults(),
                    MasterConfig.MessagesSection.defaults(), MasterConfig.BooksSection.defaults(),
                    MasterConfig.SlotsSection.defaults(), MasterConfig.SoulsSection.defaults(),
                    MasterConfig.CrystalsSection.defaults(),
                    MasterConfig.PetsSection.defaults(),
                    MasterConfig.LoreSection.defaults(), MasterConfig.IntegrationsSection.defaults(),
                    MasterConfig.ReloadSection.defaults(), MasterConfig.CommandTriggerSection.defaults(),
                    MasterConfig.MessageOnActivateSection.defaults(), MasterConfig.SetsSection.defaults(),
                    MasterConfig.EngineSection.defaults(), MasterConfig.StationsSection.defaults(),
                    MasterConfig.ApplyCuesSection.defaults(),
                    diags.all());
        }
        YamlNode root = YamlNode.compose("config.yml", yaml, diags);
        if (!root.isMapping()) {
            diags.error(DiagCode.E_CONFIG_SHAPE, "config.yml is not a YAML mapping", Source.ofFile("config.yml"));
            root = YamlNode.compose("config.yml", "", diags); // an empty mapping → every section defaults
        }
        return new MasterConfig(
                readFeatures(root.child("features"), diags),
                readCombat(root.child("combat"), diags),
                readMining(root.child("mining"), diags),
                readMessages(root.child("messages"), diags),
                readBooks(root.child("books"), diags),
                readSlots(root.child("slots"), diags),
                readSouls(root.child("souls"), diags),
                readCrystals(root.child("crystals"), diags),
                readPets(root.child("pets"), diags),
                readLore(root.child("lore"), diags),
                readIntegrations(root.child("integrations"), diags),
                readReload(root.child("reload"), diags),
                readCommandTrigger(root.child("command-trigger"), diags),
                readMessageOnActivate(root.child("message-on-activate"), diags),
                readSets(root.child("sets"), diags),
                readEngine(root.child("engine"), diags),
                readStations(root.child("stations"), diags),
                readApplyCues(root.child("apply-cues"), diags),
                diags.all());
    }

    /** Universal enchant-book apply feedback — a success cue and a fail cue, each a sound + particle-token list. */
    private static MasterConfig.ApplyCuesSection readApplyCues(YamlNode n, Diagnostics diags) {
        MasterConfig.ApplyCuesSection d = MasterConfig.ApplyCuesSection.defaults();
        YamlNode success = n.child("success");
        YamlNode fail = n.child("fail");
        return new MasterConfig.ApplyCuesSection(
                SoundCue.fromField(success, "sound", d.successSound(), diags),
                success.has("particles") ? success.stringList("particles") : d.successParticles(),
                SoundCue.fromField(fail, "sound", d.failSound(), diags),
                fail.has("particles") ? fail.stringList("particles") : d.failParticles());
    }

    /** Universal armour-set equip/unequip feedback — message-uppercase + use-set-color flags + sound/particle. */
    private static MasterConfig.SetsSection readSets(YamlNode n, Diagnostics diags) {
        MasterConfig.SetsSection d = MasterConfig.SetsSection.defaults();
        return new MasterConfig.SetsSection(
                parseBool(n.string("message-uppercase"), d.messageUppercase(), n, diags),
                parseBool(n.string("use-set-color"), d.useSetColor(), n, diags),
                SoundCue.list(n, "equip-sound", diags),
                SoundCue.list(n, "unequip-sound", diags),
                ParticleSpec.from(n.child("equip-particle"), diags),
                ParticleSpec.from(n.child("unequip-particle"), diags));
    }

    private static MasterConfig.MessageOnActivateSection readMessageOnActivate(YamlNode n, Diagnostics diags) {
        MasterConfig.MessageOnActivateSection d = MasterConfig.MessageOnActivateSection.defaults();
        // honour an explicit "" template (a pack may blank a line deliberately) rather than falling back
        String by = n.has("by-template") ? n.string("by-template") : d.byTemplate();
        String on = n.has("on-template") ? n.string("on-template") : d.onTemplate();
        return new MasterConfig.MessageOnActivateSection(
                parseBool(n.string("by-enabled"), d.byEnabled(), n, diags),
                by == null ? d.byTemplate() : by,
                parseBool(n.string("on-enabled"), d.onEnabled(), n, diags),
                on == null ? d.onTemplate() : on,
                parseBool(n.string("uppercase"), d.uppercase(), n, diags));
    }

    private static MasterConfig.FeaturesSection readFeatures(YamlNode n, Diagnostics diags) {
        MasterConfig.FeaturesSection d = MasterConfig.FeaturesSection.defaults();
        return new MasterConfig.FeaturesSection(
                parseBool(n.string("enchants"), d.enchants(), n, diags),
                parseBool(n.string("sets"), d.sets(), n, diags),
                parseBool(n.string("crystals"), d.crystals(), n, diags),
                parseBool(n.string("heroic"), d.heroic(), n, diags),
                parseBool(n.string("slots"), d.slots(), n, diags),
                parseBool(n.string("souls"), d.souls(), n, diags),
                parseBool(n.string("scrolls"), d.scrolls(), n, diags),
                parseBool(n.string("use-items"), d.useItems(), n, diags),
                parseBool(n.string("pets"), d.pets(), n, diags));
    }

    /** The universal pet knobs + the three universal pet message templates (ADR-0052). */
    private static MasterConfig.PetsSection readPets(YamlNode n, Diagnostics diags) {
        MasterConfig.PetsSection d = MasterConfig.PetsSection.defaults();
        return new MasterConfig.PetsSection(
                parseInt(n.string("max-level"), d.maxLevel(), n, diags),
                parseInt(n.string("exp-per-level"), d.expPerLevel(), n, diags),
                parseInt(n.string("exp-per-mob-kill"), d.expPerMobKill(), n, diags),
                parseDouble(n.string("exp-per-xp-point"), d.expPerXpPoint(), n, diags),
                parseInt(n.string("exp-passive-per-minute"), d.expPassivePerMinute(), n, diags),
                parseDouble(n.string("max-percent-money-cap"), d.maxPercentMoneyCap(), n, diags),
                // has() + raw value, NOT blankToNull: an explicitly BLANK template means "silent" (the
                // PetMessenger contract) — only an ABSENT key falls back to the default.
                template(n, "message-on-activate", d.messageOnActivate()),
                template(n, "message-on-end", d.messageOnEnd()),
                template(n, "message-on-cooldown", d.messageOnCooldown()),
                template(n, "message-on-fail", d.messageOnFail()),
                parseBool(n.string("uppercase"), d.uppercase(), n, diags));
    }

    private static String template(YamlNode n, String key, String fallback) {
        if (!n.has(key)) {
            return fallback;
        }
        String value = n.string(key);
        return value == null ? "" : value;
    }

    private static MasterConfig.CombatSection readCombat(YamlNode n, Diagnostics diags) {
        MasterConfig.CombatSection d = MasterConfig.CombatSection.defaults();
        return new MasterConfig.CombatSection(
                parseDouble(n.string("max-bonus-damage"), d.maxBonusDamage(), n, diags),
                parseDouble(n.string("max-bonus-reduction"), d.maxBonusReduction(), n, diags),
                parseDouble(n.string("attack-scale"), d.attackScale(), n, diags),
                parseBool(n.string("pvp"), d.pvp(), n, diags),
                parseBool(n.string("pve"), d.pve(), n, diags));
    }

    private static MasterConfig.MiningSection readMining(YamlNode n, Diagnostics diags) {
        MasterConfig.MiningSection d = MasterConfig.MiningSection.defaults();
        return new MasterConfig.MiningSection(
                parseBool(n.string("placed-block-guard"), d.placedBlockGuard(), n, diags));
    }

    private static MasterConfig.StationsSection readStations(YamlNode n, Diagnostics diags) {
        MasterConfig.StationsSection d = MasterConfig.StationsSection.defaults();
        return new MasterConfig.StationsSection(
                parseBool(n.string("anvil-guard"), d.anvilGuard(), n, diags),
                parseBool(n.string("grindstone-guard"), d.grindstoneGuard(), n, diags),
                parseBool(n.string("smithing-guard"), d.smithingGuard(), n, diags));
    }

    private static MasterConfig.EngineSection readEngine(YamlNode n, Diagnostics diags) {
        MasterConfig.EngineSection d = MasterConfig.EngineSection.defaults();
        return new MasterConfig.EngineSection(
                parseInt(n.string("offline-state-sweep-ticks"), d.offlineStateSweepTicks(), n, diags),
                parseInt(n.string("temp-block-sweep-ticks"), d.tempBlockSweepTicks(), n, diags));
    }

    private static MasterConfig.MessagesSection readMessages(YamlNode n, Diagnostics diags) {
        MasterConfig.MessagesSection d = MasterConfig.MessagesSection.defaults();
        // honour an explicit "" (a legitimate empty prefix) rather than falling back to the default
        String prefix = n.has("prefix") ? n.string("prefix") : d.prefix();
        return new MasterConfig.MessagesSection(
                prefix == null ? d.prefix() : prefix,
                parseBool(n.string("feedback"), d.feedback(), n, diags));
    }

    private static MasterConfig.BooksSection readBooks(YamlNode n, Diagnostics diags) {
        MasterConfig.BooksSection d = MasterConfig.BooksSection.defaults();
        return new MasterConfig.BooksSection(parseInt(n.string("max-success"), d.maxSuccess(), n, diags));
    }

    private static MasterConfig.SlotsSection readSlots(YamlNode n, Diagnostics diags) {
        MasterConfig.SlotsSection d = MasterConfig.SlotsSection.defaults();
        return new MasterConfig.SlotsSection(
                parseInt(n.string("base"), d.base(), n, diags),
                orDefault(n.string("lore-line"), d.loreLine()));
    }

    private static MasterConfig.SoulsSection readSouls(YamlNode n, Diagnostics diags) {
        MasterConfig.SoulsSection d = MasterConfig.SoulsSection.defaults();
        return new MasterConfig.SoulsSection(parseBool(n.string("deposit-on-any-kill"), d.depositOnAnyKill(), n, diags));
    }

    private static MasterConfig.CrystalsSection readCrystals(YamlNode n, Diagnostics diags) {
        MasterConfig.CrystalsSection d = MasterConfig.CrystalsSection.defaults();
        return new MasterConfig.CrystalsSection(
                parseInt(n.string("slots"), d.slots(), n, diags),
                parseInt(n.string("max-merge"), d.maxMerge(), n, diags));
    }

    private static MasterConfig.LoreSection readLore(YamlNode n, Diagnostics diags) {
        MasterConfig.LoreSection d = MasterConfig.LoreSection.defaults();
        return new MasterConfig.LoreSection(
                orDefault(n.string("enchant-color"), d.enchantColor()),
                // NOT orDefault: a present-but-blank level-color is meaningful (the level inherits the tier
                // colour), so only fall back to the default when the key is ABSENT.
                n.has("level-color") ? blankIfNull(n.string("level-color")) : d.levelColor(),
                orDefault(n.string("crystal-color"), d.crystalColor()),
                parseBool(n.string("roman"), d.roman(), n, diags),
                orDefault(n.string("unknown-label"), d.unknownLabel()),
                parseInt(n.string("item-wrap"), d.itemWrap(), n, diags));
    }

    private static MasterConfig.IntegrationsSection readIntegrations(YamlNode n, Diagnostics diags) {
        MasterConfig.IntegrationsSection d = MasterConfig.IntegrationsSection.defaults();
        Map<String, Boolean> named = new LinkedHashMap<>();
        for (YamlNode.Entry e : n.entries("named")) {
            String raw = e.value().scalar();
            if (raw != null && !raw.isBlank()) {
                named.put(e.key().toLowerCase(Locale.ROOT), parseBool(raw, true, e.value(), diags));
            }
        }
        return new MasterConfig.IntegrationsSection(
                parseBool(n.string("protection"), d.protection(), n, diags),
                parseBool(n.string("economy"), d.economy(), n, diags),
                named);
    }

    private static MasterConfig.ReloadSection readReload(YamlNode n, Diagnostics diags) {
        MasterConfig.ReloadSection d = MasterConfig.ReloadSection.defaults();
        return new MasterConfig.ReloadSection(
                parseBool(n.string("re-resolve-players"), d.reResolvePlayers(), n, diags),
                parseInt(n.string("auto-seconds"), d.autoSeconds(), n, diags));
    }

    private static MasterConfig.CommandTriggerSection readCommandTrigger(YamlNode n, Diagnostics diags) {
        MasterConfig.CommandTriggerSection d = MasterConfig.CommandTriggerSection.defaults();
        return new MasterConfig.CommandTriggerSection(
                parseBool(n.string("enabled"), d.enabled(), n, diags),
                orDefault(n.string("name"), d.name()),
                orDefault(n.string("description"), d.description()));
    }

    private static int parseInt(String raw, int fallback, YamlNode at, Diagnostics diags) {
        return ContentParse.intOr(raw, fallback, null, Severity.WARNING, DiagCode.W_CONFIG_NUM, at.source(), diags);
    }

    private static double parseDouble(String raw, double fallback, YamlNode at, Diagnostics diags) {
        return ContentParse.doubleOr(raw, fallback, null, Severity.WARNING, DiagCode.W_CONFIG_NUM, at.source(), diags);
    }

    /** Lenient boolean: blank falls back, non-canonical warns; {@code true}/{@code yes}/{@code on}/{@code 1} truthy. */
    private static boolean parseBool(String raw, boolean fallback, YamlNode at, Diagnostics diags) {
        return ContentParse.boolOr(raw, fallback, null, DiagCode.W_CONFIG_BOOL, at.source(), diags);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** {@code null} (a present-but-empty scalar, e.g. {@code level-color:}) reads as the empty string. */
    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
