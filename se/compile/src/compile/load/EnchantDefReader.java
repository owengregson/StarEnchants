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
import schema.diag.DiagCode;
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
            "display", "description", "tier", "applies-to", "trigger", "disabled-worlds", "disabled-environments", "group",
            "repeat", "initial-delay", "levels", "chance", "cooldown", "soul-cost", "condition",
            "no-souls-effects", "requires", "blacklist", "removes-required", "suppress-immune", "stacking");
    private static final Set<String> LEVEL_KEYS = Set.of(
            "chance", "cooldown", "soul-cost", "condition", "repeat", "initial-delay",
            "effects", "no-souls-effects", "abilities");
    private static final Set<String> ABILITY_KEYS = Set.of(
            "trigger", "disabled-worlds", "disabled-environments", "group", "repeat", "initial-delay", "chance", "cooldown", "soul-cost",
            "condition", "effects", "no-souls-effects", "suppress-immune");

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
            diags.error(DiagCode.E_LOAD_ENCHANT, "enchant file '" + baseKey + "' must be a YAML mapping", fileSource);
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
        List<String> disabledWorlds = root.stringList("disabled-worlds");
        List<String> disabledEnvironments = root.stringList("disabled-environments");
        String group = ContentParse.blankToNull(root.string("group"));
        // §G relationships: pure metadata, evaluated by ItemEnchanter at apply.
        List<String> requires = root.stringList("requires");
        List<String> blacklist = root.stringList("blacklist");
        boolean removesRequired = ContentParse.boolOr(root.string("removes-required"), false, "removes-required",
                DiagCode.W_LOAD_BOOL, root.sourceOf("removes-required"), diags);
        if (removesRequired && requires.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_ENCHANT_RELATIONSHIPS,
                    "enchant '" + baseKey + "' sets removes-required but declares no 'requires'",
                    root.sourceOf("removes-required"));
        }
        int repeatTicks = ContentParse.optInt(root, "repeat", 0, diags);
        int repeatInitialDelayTicks = root.has("initial-delay")
                ? ContentParse.optInt(root, "initial-delay", repeatTicks, diags) : repeatTicks;
        // Per-enchant suppression immunity (Silence & derivatives): when true, THIS enchant's abilities can never
        // be disabled, so a permanent buff survives while the wearer's other enchants are still silenced.
        boolean suppressImmune = ContentParse.boolOr(root.string("suppress-immune"), false, "suppress-immune",
                DiagCode.W_LOAD_BOOL, root.sourceOf("suppress-immune"), diags);
        EnchantDef.Stacking stacking = EnchantDef.Stacking.parse(root.string("stacking"));
        if (stacking == null) {
            diags.error(DiagCode.E_LOAD_ENCHANT,
                    "enchant '" + baseKey + "' stacking must be HIGHEST or EACH",
                    root.sourceOf("stacking"));
            stacking = EnchantDef.Stacking.EACH;
        }

        Map<Integer, YamlNode> levelNodes = new LinkedHashMap<>();
        for (YamlNode.Entry entry : root.entries("levels")) {
            Integer level = ContentParse.parseInt(entry.key());
            Source levelSource = entry.value().source();
            if (level == null || level < 1) {
                diags.error(DiagCode.E_LOAD_ENCHANT_LEVEL,
                        "level key must be a positive integer, got '" + entry.key() + "'", levelSource);
                continue;
            }
            if (!entry.value().isMapping()) {
                diags.error(DiagCode.E_LOAD_ENCHANT_LEVEL,
                        "level " + level + " of '" + baseKey + "' must be a mapping", levelSource);
                continue;
            }
            ContentParse.warnUnknownKeys(entry.value(), LEVEL_KEYS, diags);
            levelNodes.put(level, entry.value());
        }

        TreeSet<Integer> levelSet = new TreeSet<>(levelNodes.keySet());
        if (levelSet.isEmpty()) {
            diags.error(DiagCode.E_LOAD_ENCHANT_LEVELS, "enchant '" + baseKey + "' declares no levels (need a 'levels:' map)",
                    root.sourceOf("levels"));
        }
        int maxLevel = levelSet.isEmpty() ? 0 : levelSet.last();

        List<AbilityDef> abilities = new ArrayList<>();
        for (int level : levelSet) {
            YamlNode lvl = levelNodes.get(level); // levelSet derives from levelNodes — never null
            int levelRepeat = lvl.has("repeat")
                    ? ContentParse.optInt(lvl, "repeat", repeatTicks, diags) : repeatTicks;
            int levelInitialDelay = lvl.has("initial-delay")
                    ? ContentParse.optInt(lvl, "initial-delay", levelRepeat, diags)
                    : lvl.has("repeat") ? levelRepeat : repeatInitialDelayTicks;
            List<YamlNode> authoredAbilities = lvl.has("abilities") ? lvl.items("abilities") : List.of();
            if (authoredAbilities.isEmpty()) {
                if (lvl.has("abilities")) {
                    diags.error(DiagCode.E_LOAD_ENCHANT,
                            "enchant '" + baseKey + "' level " + level + " declares an empty abilities list",
                            lvl.sourceOf("abilities"));
                    continue;
                }
                if (triggers.isEmpty()) {
                    diags.error(DiagCode.E_LOAD_ENCHANT_TRIGGER,
                            "enchant '" + baseKey + "' level " + level + " declares no trigger",
                            lvl.sourceOf("effects"));
                }
                abilities.add(ability(baseKey + "/" + level, baseKey, level, lvl, root, root, triggers,
                        disabledWorlds, disabledEnvironments, group, levelRepeat, levelInitialDelay,
                        suppressImmune, nextDefId, diags));
                continue;
            }
            int index = 0;
            for (YamlNode block : authoredAbilities) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_ENCHANT,
                            "ability " + index + " of enchant '" + baseKey + "' level " + level
                                    + " must be a mapping",
                            block.source());
                    index++;
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                String stableKey = baseKey + "/" + level + (index == 0 ? "" : "/a" + index);
                List<String> abilityTriggers = block.has("trigger") ? block.stringList("trigger") : triggers;
                List<String> abilityWorlds = block.has("disabled-worlds")
                        ? block.stringList("disabled-worlds") : disabledWorlds;
                List<String> abilityEnvironments = block.has("disabled-environments")
                        ? block.stringList("disabled-environments") : disabledEnvironments;
                String abilityGroup = block.has("group")
                        ? ContentParse.blankToNull(block.string("group")) : group;
                int abilityRepeat = block.has("repeat")
                        ? ContentParse.optInt(block, "repeat", levelRepeat, diags) : levelRepeat;
                int abilityInitialDelay = block.has("initial-delay")
                        ? ContentParse.optInt(block, "initial-delay", abilityRepeat, diags)
                        : block.has("repeat") ? abilityRepeat : levelInitialDelay;
                boolean abilitySuppressImmune = block.has("suppress-immune")
                        ? ContentParse.boolOr(block.string("suppress-immune"), suppressImmune, "suppress-immune",
                                DiagCode.W_LOAD_BOOL, block.sourceOf("suppress-immune"), diags)
                        : suppressImmune;
                abilities.add(ability(stableKey, baseKey, level, block, lvl, root, abilityTriggers, abilityWorlds,
                        abilityEnvironments, abilityGroup, abilityRepeat, abilityInitialDelay,
                        abilitySuppressImmune, nextDefId, diags));
                index++;
            }
        }

        EnchantDef def = new EnchantDef(baseKey, display, description == null ? "" : description,
                tier, appliesTo, maxLevel, requires, blacklist, removesRequired, stacking, fileSource);
        return new Parsed(def, abilities);
    }

    private static AbilityDef ability(String stableKey, String baseKey, int level, YamlNode node,
                                      YamlNode fallback, YamlNode root, List<String> triggers,
                                      List<String> disabledWorlds, List<String> disabledEnvironments,
                                      String group, int repeatTicks, int repeatInitialDelayTicks,
                                      boolean suppressImmune,
                                      IntSupplier nextDefId, Diagnostics diags) {
        if (triggers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_ENCHANT_TRIGGER,
                    "enchant ability '" + stableKey + "' declares no trigger", node.sourceOf("trigger"));
        }
        double chance = ContentParse.resolveChance(knobNode(node, fallback, root, "chance"), "chance", diags);
        int cooldown = ContentParse.resolveInt(knobNode(node, fallback, root, "cooldown"), "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(knobNode(node, fallback, root, "soul-cost"), "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(
                ContentParse.resolveString(knobNode(node, fallback, root, "condition"), "condition", diags));
        condition = withDisabledEnvironments(stableKey, condition, disabledEnvironments,
                node.sourceOf("disabled-environments"), diags);
        List<EffectLine> effects = effectsFor(stableKey, node, diags);
        YamlNode noSoulNode = knobNode(node, fallback, root, "no-souls-effects");
        List<EffectLine> noSoulEffects = noSoulNode != null && noSoulNode.has("no-souls-effects")
                ? ContentParse.effectItems(noSoulNode, "no-souls-effects", diags)
                : List.of();
        return new AbilityDef(
                SourceKind.ENCHANT, stableKey, nextDefId.getAsInt(), level, chance, cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, baseKey, baseKey, group, null, repeatTicks,
                repeatInitialDelayTicks, node.source(), 0, suppressImmune, noSoulEffects);
    }

    private static String withDisabledEnvironments(String stableKey, String condition,
                                                   List<String> disabledEnvironments,
                                                   Source source, Diagnostics diags) {
        if (disabledEnvironments.isEmpty()) {
            return condition;
        }
        List<String> gates = new ArrayList<>();
        for (String raw : disabledEnvironments) {
            String environment = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (!environment.matches("[A-Z][A-Z0-9_]*")) {
                diags.error(DiagCode.E_LOAD_ENCHANT,
                        "enchant ability '" + stableKey + "' has invalid disabled environment '" + raw + "'",
                        source, "use an environment name such as NORMAL, NETHER, or THE_END");
                continue;
            }
            gates.add("%actor.environment% != \"" + environment + "\"");
        }
        if (gates.isEmpty()) {
            return condition;
        }
        String environmentGate = String.join(" && ", gates);
        return condition == null ? environmentGate : "(" + condition + ") && " + environmentGate;
    }

    /** The node a knob is read from: the level override if it declares the key, else the file root. */
    private static YamlNode knobNode(YamlNode node, YamlNode fallback, YamlNode root, String key) {
        if (node != null && node.has(key)) {
            return node;
        }
        return fallback != null && fallback.has(key) ? fallback : root;
    }

    /** The level or ability node's own effects list. */
    private static List<EffectLine> effectsFor(String stableKey, YamlNode node, Diagnostics diags) {
        List<EffectLine> effects = node.has("effects")
                ? ContentParse.effectItems(node, "effects", diags)
                : new ArrayList<>();
        if (effects.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "enchant ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return effects;
    }
}
