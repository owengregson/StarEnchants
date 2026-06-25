package compile.load;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads the master {@code config.yml} into an immutable {@link MasterConfig} (§L) — like {@link ItemsLoader}
 * but over a single FILE: each top-level section read by a {@code read*} helper, falling back per-field to
 * the section default. Reuses the content diagnostics machinery so {@code /se reload --dry-run} surfaces
 * config faults the same way. Never throws — absent/unreadable yields {@link MasterConfig#defaults()},
 * malformed yields a diagnostic plus defaults.
 */
public final class MasterConfigLoader {

    private MasterConfigLoader() {
    }

    /** Load {@code config.yml} (or {@link MasterConfig#defaults()} if the file is absent/unreadable). */
    public static MasterConfig load(Path configFile) {
        Diagnostics diags = new Diagnostics();
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return MasterConfig.defaults();
        }
        String yaml;
        try {
            yaml = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diags.error("E_CONFIG_IO", "could not read config.yml: " + e.getMessage(), Source.ofFile("config.yml"));
            return new MasterConfig(MasterConfig.FeaturesSection.defaults(), MasterConfig.CombatSection.defaults(),
                    MasterConfig.MessagesSection.defaults(),
                    MasterConfig.SlotsSection.defaults(), MasterConfig.SoulsSection.defaults(),
                    MasterConfig.CrystalsSection.defaults(), MasterConfig.HeroicSection.defaults(),
                    MasterConfig.LoreSection.defaults(), MasterConfig.IntegrationsSection.defaults(),
                    MasterConfig.ReloadSection.defaults(), MasterConfig.CommandTriggerSection.defaults(),
                    diags.all());
        }
        YamlNode root = YamlNode.compose("config.yml", yaml, diags);
        if (!root.isMapping()) {
            diags.error("E_CONFIG_SHAPE", "config.yml is not a YAML mapping", Source.ofFile("config.yml"));
            root = YamlNode.compose("config.yml", "", diags); // an empty mapping → every section defaults
        }
        return new MasterConfig(
                readFeatures(root.child("features"), diags),
                readCombat(root.child("combat"), diags),
                readMessages(root.child("messages"), diags),
                readSlots(root.child("slots"), diags),
                readSouls(root.child("souls"), diags),
                readCrystals(root.child("crystals"), diags),
                readHeroic(root.child("heroic"), diags),
                readLore(root.child("lore"), diags),
                readIntegrations(root.child("integrations"), diags),
                readReload(root.child("reload"), diags),
                readCommandTrigger(root.child("command-trigger"), diags),
                diags.all());
    }

    private static MasterConfig.FeaturesSection readFeatures(YamlNode n, Diagnostics diags) {
        MasterConfig.FeaturesSection d = MasterConfig.FeaturesSection.defaults();
        return new MasterConfig.FeaturesSection(
                parseBool(n.string("enchants"), d.enchants()),
                parseBool(n.string("sets"), d.sets()),
                parseBool(n.string("crystals"), d.crystals()),
                parseBool(n.string("heroic"), d.heroic()),
                parseBool(n.string("slots"), d.slots()),
                parseBool(n.string("souls"), d.souls()),
                parseBool(n.string("scrolls"), d.scrolls()));
    }

    private static MasterConfig.CombatSection readCombat(YamlNode n, Diagnostics diags) {
        MasterConfig.CombatSection d = MasterConfig.CombatSection.defaults();
        return new MasterConfig.CombatSection(
                parseDouble(n.string("max-bonus-damage"), d.maxBonusDamage(), n, diags),
                parseDouble(n.string("max-bonus-reduction"), d.maxBonusReduction(), n, diags),
                parseBool(n.string("pvp"), d.pvp()),
                parseBool(n.string("pve"), d.pve()));
    }

    private static MasterConfig.MessagesSection readMessages(YamlNode n, Diagnostics diags) {
        MasterConfig.MessagesSection d = MasterConfig.MessagesSection.defaults();
        // honour an explicit "" (a legitimate empty prefix) rather than falling back to the default
        String prefix = n.has("prefix") ? n.string("prefix") : d.prefix();
        return new MasterConfig.MessagesSection(
                prefix == null ? d.prefix() : prefix,
                parseBool(n.string("feedback"), d.feedback()));
    }

    private static MasterConfig.SlotsSection readSlots(YamlNode n, Diagnostics diags) {
        MasterConfig.SlotsSection d = MasterConfig.SlotsSection.defaults();
        return new MasterConfig.SlotsSection(parseInt(n.string("base"), d.base(), n, diags));
    }

    private static MasterConfig.SoulsSection readSouls(YamlNode n, Diagnostics diags) {
        MasterConfig.SoulsSection d = MasterConfig.SoulsSection.defaults();
        return new MasterConfig.SoulsSection(parseBool(n.string("deposit-on-any-kill"), d.depositOnAnyKill()));
    }

    private static MasterConfig.CrystalsSection readCrystals(YamlNode n, Diagnostics diags) {
        MasterConfig.CrystalsSection d = MasterConfig.CrystalsSection.defaults();
        return new MasterConfig.CrystalsSection(
                parseInt(n.string("slots"), d.slots(), n, diags),
                parseInt(n.string("max-stack"), d.maxStack(), n, diags));
    }

    private static MasterConfig.HeroicSection readHeroic(YamlNode n, Diagnostics diags) {
        MasterConfig.HeroicSection d = MasterConfig.HeroicSection.defaults();
        return new MasterConfig.HeroicSection(
                parseDouble(n.string("max-outgoing-factor"), d.maxOutgoingFactor(), n, diags));
    }

    private static MasterConfig.LoreSection readLore(YamlNode n, Diagnostics diags) {
        MasterConfig.LoreSection d = MasterConfig.LoreSection.defaults();
        return new MasterConfig.LoreSection(
                orDefault(n.string("enchant-color"), d.enchantColor()),
                orDefault(n.string("level-color"), d.levelColor()),
                orDefault(n.string("crystal-color"), d.crystalColor()),
                parseBool(n.string("roman"), d.roman()),
                orDefault(n.string("unknown-label"), d.unknownLabel()));
    }

    private static MasterConfig.IntegrationsSection readIntegrations(YamlNode n, Diagnostics diags) {
        MasterConfig.IntegrationsSection d = MasterConfig.IntegrationsSection.defaults();
        Map<String, Boolean> named = new LinkedHashMap<>();
        for (YamlNode.Entry e : n.entries("named")) {
            String raw = e.value().scalar();
            if (raw != null && !raw.isBlank()) {
                named.put(e.key().toLowerCase(Locale.ROOT), parseBool(raw, true));
            }
        }
        return new MasterConfig.IntegrationsSection(
                parseBool(n.string("protection"), d.protection()),
                parseBool(n.string("economy"), d.economy()),
                named);
    }

    private static MasterConfig.ReloadSection readReload(YamlNode n, Diagnostics diags) {
        MasterConfig.ReloadSection d = MasterConfig.ReloadSection.defaults();
        return new MasterConfig.ReloadSection(
                parseBool(n.string("re-resolve-players"), d.reResolvePlayers()),
                parseInt(n.string("auto-seconds"), d.autoSeconds(), n, diags));
    }

    private static MasterConfig.CommandTriggerSection readCommandTrigger(YamlNode n, Diagnostics diags) {
        MasterConfig.CommandTriggerSection d = MasterConfig.CommandTriggerSection.defaults();
        return new MasterConfig.CommandTriggerSection(
                parseBool(n.string("enabled"), d.enabled()),
                orDefault(n.string("name"), d.name()),
                orDefault(n.string("description"), d.description()));
    }

    private static int parseInt(String raw, int fallback, YamlNode at, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            diags.warning("W_CONFIG_NUM", "invalid number '" + raw + "', using " + fallback, at.source());
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback, YamlNode at, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            diags.warning("W_CONFIG_NUM", "invalid number '" + raw + "', using " + fallback, at.source());
            return fallback;
        }
    }

    /** Lenient boolean: blank/unparseable falls back; {@code true}/{@code yes}/{@code on}/{@code 1} are truthy. */
    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("1")) {
            return true;
        }
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("0")) {
            return false;
        }
        return fallback;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
