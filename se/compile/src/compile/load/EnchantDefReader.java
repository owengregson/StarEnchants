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
 * Reads one authored enchant file into its {@link EnchantDef} plus one {@link AbilityDef} per level
 * (ADR-0014). The level set is exactly the keys under {@code levels:}; each level's knobs fall back to a
 * same-named root knob as a shared default. A bad field/level is warned-and-skipped, never thrown.
 */
final class EnchantDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "applies-to", "trigger", "disabled-worlds", "group",
            "repeat", "levels", "chance", "cooldown", "soul-cost", "condition",
            "requires", "blacklist", "removes-required");
    private static final Set<String> LEVEL_KEYS = Set.of(
            "chance", "cooldown", "soul-cost", "condition", "effects");

    private EnchantDefReader() {
    }

    record Parsed(EnchantDef def, List<AbilityDef> abilities) {
    }

    /** Test/convenience entry: no folder-derived tier. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        return read(baseKey, null, root, nextDefId, diags);
    }

    /** Parse one enchant. The in-file {@code tier:} overrides {@code folderTier} (a mismatch warns). */
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
        // §G relationships: pure metadata, evaluated by ItemEnchanter at apply.
        List<String> requires = root.stringList("requires");
        List<String> blacklist = root.stringList("blacklist");
        boolean removesRequired = "true".equalsIgnoreCase(root.string("removes-required"));
        if (removesRequired && requires.isEmpty()) {
            diags.warning("load.enchant.relationships",
                    "enchant '" + baseKey + "' sets removes-required but declares no 'requires'",
                    root.sourceOf("removes-required"));
        }
        int repeatTicks = ContentParse.optInt(root, "repeat", 0, diags);

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

        TreeSet<Integer> levelSet = new TreeSet<>(levelNodes.keySet());
        if (levelSet.isEmpty()) {
            diags.error("load.enchant.levels", "enchant '" + baseKey + "' declares no levels (need a 'levels:' map)",
                    root.sourceOf("levels"));
        }
        int maxLevel = levelSet.isEmpty() ? 0 : levelSet.last();

        List<AbilityDef> abilities = new ArrayList<>();
        for (int level : levelSet) {
            YamlNode lvl = levelNodes.get(level); // levelSet derives from levelNodes — never null
            double chance = ContentParse.resolveChance(knobNode(lvl, root, "chance"), "chance", diags);
            int cooldown = ContentParse.resolveInt(knobNode(lvl, root, "cooldown"), "cooldown", 0, diags);
            int soulCost = ContentParse.resolveInt(knobNode(lvl, root, "soul-cost"), "soul-cost", 0, diags);
            String condition = ContentParse.blankToNull(
                    ContentParse.resolveString(knobNode(lvl, root, "condition"), "condition", diags));
            List<EffectLine> effects = effectsFor(baseKey, level, lvl, diags);

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
                tier, appliesTo, maxLevel, requires, blacklist, removesRequired, fileSource);
        return new Parsed(def, abilities);
    }

    /** The node a knob is read from: the level override if it declares the key, else the file root. */
    private static YamlNode knobNode(YamlNode lvl, YamlNode root, String key) {
        return lvl != null && lvl.has(key) ? lvl : root;
    }

    /** The level's own {@code effects:} list; an empty list warns (a no-op ability is almost always a mistake). */
    private static List<EffectLine> effectsFor(String baseKey, int level, YamlNode lvl, Diagnostics diags) {
        List<EffectLine> effects = lvl.has("effects")
                ? ContentParse.effectItems(lvl, "effects", diags)
                : new ArrayList<>();
        if (effects.isEmpty()) {
            diags.warning("load.effects", "level " + level + " of '" + baseKey + "' declares no effects",
                    lvl.sourceOf("effects"));
        }
        return effects;
    }
}
