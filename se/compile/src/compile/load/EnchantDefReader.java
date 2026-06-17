package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored enchant file (a composed {@link YamlNode} mapping) into its metadata
 * {@link EnchantDef} plus one {@link AbilityDef} per level (docs/architecture.md §10; ADR-0014,
 * ADR-0016). The stable key is path-derived — the base key plus {@code /<level>} — so an item storing
 * {@code (enchants/lifesteal, 3)} resolves to {@code enchants/lifesteal/3}.
 *
 * <p>Two authoring shapes, unified here (ADR-0016):
 * <ul>
 *   <li><strong>v1</strong> — everything per level under {@code levels: { 1: {chance, effects}, … }}.
 *       The level set is the declared level keys; each level reads its own knobs/effects.</li>
 *   <li><strong>v2 templated</strong> — shared {@code effects:}/{@code scale:}/knobs at the file root,
 *       expanded over {@code 1..max-level}; {@code $token} scale references fill the varying numbers,
 *       and {@code levels: { N: { … } }} deep-merges over the scaled defaults (a level's scalar knob
 *       wins; {@code effects:} replaces the scaled list, {@code effects+:} appends to it).</li>
 * </ul>
 * Presence of a root {@code effects:} or {@code scale:} switches to templated mode; otherwise the v1
 * path runs (so existing files load byte-for-byte). Every fault is a {@code file:line:col} diagnostic;
 * a bad field/level is warned-and-skipped, never thrown (§7, §10).
 */
final class EnchantDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "applies-to", "trigger", "disabled-worlds", "group",
            "repeat", "scale", "max-level", "levels", "effects", "chance", "cooldown", "soul-cost", "condition");
    private static final Set<String> LEVEL_KEYS = Set.of(
            "chance", "cooldown", "soul-cost", "condition", "effects", "effects+");

    private EnchantDefReader() {
    }

    /** One enchant's parsed output: its metadata and the per-level abilities it expands into. */
    record Parsed(EnchantDef def, List<AbilityDef> abilities) {
    }

    /** Test/convenience entry: no folder-derived tier (the in-file {@code tier:} or {@code null} applies). */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        return read(baseKey, null, root, nextDefId, diags);
    }

    /**
     * Parse one enchant. {@code baseKey} is the path-derived key, e.g. {@code enchants/lifesteal};
     * {@code folderTier} is the tier derived from the file's subfolder (may be {@code null}). The
     * in-file {@code tier:} overrides it (a mismatch warns).
     */
    static Parsed read(String baseKey, String folderTier, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.enchant", "enchant file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        String description = ContentParse.descriptionOf(root);
        String tier = ContentParse.resolveTier(folderTier, root, diags);
        List<String> appliesTo = root.stringList("applies-to");
        List<String> triggers = root.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error("load.enchant.trigger", "enchant '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        List<String> disabledWorlds = root.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(root.string("group"));
        int repeatTicks = ContentParse.optInt(root, "repeat", 0, diags);
        ScaleEnv scale = ScaleEnv.read(root, diags);
        boolean templated = root.has("effects") || root.has("scale");

        // Gather declared level override nodes (must be mappings), and the explicit level set.
        Map<Integer, YamlNode> levelNodes = new LinkedHashMap<>();
        for (YamlNode.Entry entry : root.entries("levels")) {
            Integer level = ContentParse.parseInt(entry.key());
            Source levelSource = entry.value().source();
            if (level == null || level < 1) {
                diags.error("load.enchant.level",
                        "level key must be a positive integer, got '" + entry.key() + "'", levelSource);
                continue;
            }
            if (!entry.value().isMapping()) {
                diags.error("load.enchant.level",
                        "level " + level + " of '" + baseKey + "' must be a mapping", levelSource);
                continue;
            }
            ContentParse.warnUnknownKeys(entry.value(), LEVEL_KEYS, diags);
            levelNodes.put(level, entry.value());
        }

        int declaredMax = levelNodes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maxLevel = ContentParse.optInt(root, "max-level", declaredMax, diags);
        // The level set: 1..maxLevel for templated files; the declared keys for v1 files.
        TreeSet<Integer> levelSet = new TreeSet<>(levelNodes.keySet());
        if (templated) {
            for (int level = 1; level <= maxLevel; level++) {
                levelSet.add(level);
            }
        }
        if (levelSet.isEmpty()) {
            diags.error("load.enchant.levels", "enchant '" + baseKey + "' declares no levels (need 'levels:' "
                    + "or a root 'effects:' with 'max-level')", root.sourceOf("levels"));
        }

        List<AbilityDef> abilities = new ArrayList<>();
        for (int level : levelSet) {
            YamlNode lvl = levelNodes.get(level); // null = a templated level with no override
            double chance = ContentParse.resolveChance(knobNode(lvl, root, "chance"), "chance", level, scale, diags);
            int cooldown = ContentParse.resolveInt(knobNode(lvl, root, "cooldown"), "cooldown", 0, level, scale, diags);
            int soulCost = ContentParse.resolveInt(knobNode(lvl, root, "soul-cost"), "soul-cost", 0, level, scale, diags);
            String condition = ContentParse.blankToNull(
                    ContentParse.resolveString(knobNode(lvl, root, "condition"), "condition", level, scale, diags));
            List<EffectLine> effects = effectsFor(baseKey, level, lvl, root, templated, scale, diags);

            abilities.add(new AbilityDef(
                    SourceKind.ENCHANT,
                    baseKey + "/" + level,
                    nextDefId.getAsInt(),
                    level,
                    chance,
                    cooldown,
                    soulCost,
                    triggers,
                    disabledWorlds,
                    condition,
                    effects,
                    baseKey,
                    baseKey,
                    group,
                    null,
                    repeatTicks,
                    lvl != null ? lvl.source() : fileSource,
                    0));
        }

        EnchantDef def = new EnchantDef(baseKey, display, description == null ? "" : description,
                tier, appliesTo, Math.max(maxLevel, levelSet.isEmpty() ? 0 : levelSet.last()), fileSource);
        return new Parsed(def, abilities);
    }

    /** The node a knob is read from: the level override if it declares the key, else the file root. */
    private static YamlNode knobNode(YamlNode lvl, YamlNode root, String key) {
        return lvl != null && lvl.has(key) ? lvl : root;
    }

    /**
     * The effects for one level: the level's own {@code effects:} (replace) if present, else the
     * templated root {@code effects:} (when templated), with a level {@code effects+:} appended. A
     * level with no effects from any source is warned (a no-op ability is almost always a mistake).
     */
    private static List<EffectLine> effectsFor(String baseKey, int level, YamlNode lvl, YamlNode root,
                                               boolean templated, ScaleEnv scale, Diagnostics diags) {
        List<EffectLine> effects;
        if (lvl != null && lvl.has("effects")) {
            effects = ContentParse.effectItems(lvl, "effects", level, scale, diags);
        } else if (templated && root.has("effects")) {
            effects = ContentParse.effectItems(root, "effects", level, scale, diags);
        } else {
            effects = new ArrayList<>();
        }
        if (lvl != null && lvl.has("effects+")) {
            effects.addAll(ContentParse.effectItems(lvl, "effects+", level, scale, diags));
        }
        if (effects.isEmpty()) {
            Source where = lvl != null ? lvl.sourceOf("effects") : root.sourceOf("effects");
            diags.warning("load.effects", "level " + level + " of '" + baseKey + "' declares no effects", where);
        }
        return effects;
    }
}
