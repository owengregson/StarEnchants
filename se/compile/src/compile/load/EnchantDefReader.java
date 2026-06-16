package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored enchant file (a composed {@link YamlNode} mapping) into its metadata
 * {@link EnchantDef} plus one {@link AbilityDef} per declared level (docs/architecture.md §10;
 * ADR-0014). Shared identity fields live at the top of the file; per-level fields under
 * {@code levels:}. The stable key is path-derived — the base key plus {@code /<level>} — so an
 * item storing {@code (enchants/lifesteal, 3)} resolves to {@code enchants/lifesteal/3}.
 *
 * <p>Every fault is a {@code file:line:col} diagnostic; a bad field or level is warned-and-skipped,
 * never thrown (§7, §10). A blocking diagnostic on a required field (no trigger, no levels) makes
 * the whole load non-publishable, but the reader still parses as much as it can for reporting.
 */
final class EnchantDefReader {

    private EnchantDefReader() {
    }

    /** One enchant's parsed output: its metadata and the per-level abilities it expands into. */
    record Parsed(EnchantDef def, List<AbilityDef> abilities) {
    }

    /** Parse one enchant. {@code baseKey} is the path-derived key, e.g. {@code enchants/lifesteal}. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.enchant", "enchant file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }

        String display = blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey; // non-fatal: default the display name to the key (absent OR blank)
        }
        String description = blankToNull(root.string("description"));
        List<String> appliesTo = root.stringList("applies-to");
        List<String> triggers = root.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error("load.enchant.trigger", "enchant '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        List<String> disabledWorlds = root.stringList("disabled-worlds");
        String group = blankToNull(root.string("group"));
        int repeatTicks = optInt(root, "repeat", 0, diags);

        List<AbilityDef> abilities = new ArrayList<>();
        List<YamlNode.Entry> levels = root.entries("levels");
        if (levels.isEmpty()) {
            diags.error("load.enchant.levels", "enchant '" + baseKey + "' declares no levels",
                    root.sourceOf("levels"));
        }
        int maxDeclared = 0;
        for (YamlNode.Entry entry : levels) {
            Integer level = parseInt(entry.key());
            Source levelSource = entry.value().source();
            if (level == null || level < 1) {
                diags.error("load.enchant.level",
                        "level key must be a positive integer, got '" + entry.key() + "'", levelSource);
                continue;
            }
            YamlNode lvl = entry.value();
            if (!lvl.isMapping()) {
                diags.error("load.enchant.level",
                        "level " + level + " of '" + baseKey + "' must be a mapping", levelSource);
                continue;
            }
            maxDeclared = Math.max(maxDeclared, level);

            double chance = clampChance(optDouble(lvl, "chance", 100.0, diags), lvl.sourceOf("chance"), diags);
            int cooldown = optInt(lvl, "cooldown", 0, diags);
            int soulCost = optInt(lvl, "soul-cost", 0, diags);
            String condition = blankToNull(lvl.string("condition"));
            List<EffectLine> effects = effectLines(lvl, baseKey, level, diags);

            abilities.add(new AbilityDef(
                    SourceKind.ENCHANT,
                    baseKey + "/" + level,    // path-derived per-level stable key
                    nextDefId.getAsInt(),
                    level,
                    chance,
                    cooldown,
                    soulCost,
                    triggers,
                    disabledWorlds,
                    condition,
                    effects,
                    baseKey,                  // suppressKey: DISABLE_* cancels by the enchant identity
                    baseKey,                  // cdScopeEnchant: per-enchant cooldown, shared across levels
                    group,                    // cdScopeGroup (may be null)
                    null,                     // cdScopeType: deferred
                    repeatTicks,
                    levelSource));
        }

        int maxLevel = optInt(root, "max-level", maxDeclared, diags);
        EnchantDef def = new EnchantDef(baseKey, display,
                description == null ? "" : description, appliesTo, maxLevel, fileSource);
        return new Parsed(def, abilities);
    }

    private static List<EffectLine> effectLines(YamlNode lvl, String baseKey, int level, Diagnostics diags) {
        List<String> raw = lvl.stringList("effects");
        Source source = lvl.sourceOf("effects");
        if (raw.isEmpty()) {
            diags.warning("load.enchant.effects",
                    "level " + level + " of '" + baseKey + "' declares no effects", source);
        }
        List<EffectLine> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(EffectLine.parse(line, source));
        }
        return out;
    }

    private static double clampChance(double chance, Source source, Diagnostics diags) {
        // NaN slips past a naive range check (NaN < 0 and NaN > 100 are both false), so guard it
        // explicitly — a NaN activation chance must never reach the runtime.
        if (Double.isNaN(chance) || chance < 0.0 || chance > 100.0) {
            diags.error("load.chance", "chance must be a number in [0,100], got " + chance, source);
            return Double.isNaN(chance) ? 0.0 : Math.max(0.0, Math.min(100.0, chance));
        }
        return chance;
    }

    private static int optInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException bad) {
            diags.error("load.int", "'" + key + "' must be an integer, got '" + raw + "'", node.sourceOf(key));
            return fallback;
        }
    }

    private static double optDouble(YamlNode node, String key, double fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException bad) {
            diags.error("load.double", "'" + key + "' must be a number, got '" + raw + "'", node.sourceOf(key));
            return fallback;
        }
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
