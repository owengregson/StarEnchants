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
 * Reads one authored enchant file into its {@link EnchantDef} plus its {@link AbilityDef}s (ADR-0014). The
 * level set is exactly the keys under {@code levels:}; each level's knobs fall back to a same-named root knob
 * as a shared default. A level is normally ONE ability, but may fan into several via an {@code abilities:}
 * list — the first keeps the bare {@code <base>/<level>} key items store, the rest take {@code /a1, /a2, …}.
 * A bad field/level is warned-and-skipped, never thrown.
 *
 * <p>{@code group:} is the one knob that reads differently at the two scopes: at the ROOT it is the enchant's
 * family, the key suppression matches on, and on a BLOCK it narrows only that ability's IMPACT source scope
 * (R-QC40). An arm and its payload can therefore be a private pair inside a family everything else shares.
 */
final class EnchantDefReader {

    private static final Set<String> ROOT_KEYS = ContentParse.withEnvelopeKnobs(
            "display", "description", "tier", "applies-to", "trigger", "disabled-worlds", "group",
            "repeat", "repeat-delay", "levels", "chance", "cooldown", "soul-cost", "soul-cost-growth", "soul-cost-cap",
            "soul-cost-decay-period", "no-souls-message", "condition",
            "requires", "blacklist", "removes-required", "suppress-immune");
    private static final Set<String> LEVEL_KEYS = ContentParse.withEnvelopeKnobs(
            "chance", "cooldown", "soul-cost", "soul-cost-growth", "soul-cost-cap", "soul-cost-decay-period",
            "no-souls-message", "condition", "effects", "abilities");
    private static final Set<String> ABILITY_KEYS = ContentParse.withEnvelopeKnobs(
            "trigger", "chance", "cooldown", "soul-cost", "soul-cost-growth", "soul-cost-cap",
            "soul-cost-decay-period", "no-souls-message", "condition", "repeat", "repeat-delay", "effects",
            "group");

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
        if (triggers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_ENCHANT_TRIGGER, "enchant '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        List<String> disabledWorlds = root.stringList("disabled-worlds");
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
        // R-QC35b: ticks before the FIRST run; -1 keeps the historical shape (one full period out).
        int repeatDelayTicks = ContentParse.optInt(root, "repeat-delay", -1, diags);
        // Per-enchant suppression immunity (Silence & derivatives): when true, THIS enchant's abilities can never
        // be disabled, so a permanent buff survives while the wearer's other enchants are still silenced.
        boolean suppressImmune = ContentParse.boolOr(root.string("suppress-immune"), false, "suppress-immune",
                DiagCode.W_LOAD_BOOL, root.sourceOf("suppress-immune"), diags);

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
            String levelKey = baseKey + "/" + level;
            if (!lvl.has("abilities")) {
                abilities.add(ability(levelKey, level, null, lvl, root, baseKey, triggers, disabledWorlds, group,
                        repeatTicks, repeatDelayTicks, suppressImmune, nextDefId, diags));
                continue;
            }
            if (lvl.has("effects")) {
                diags.error(DiagCode.E_LOAD_ENCHANT_LEVEL, "level " + level + " of '" + baseKey
                        + "' declares both 'abilities' and 'effects' — use one or the other",
                        lvl.sourceOf("abilities"),
                        "move the 'effects' list into the first 'abilities' entry");
            }
            int index = 0;
            for (YamlNode block : lvl.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_ENCHANT_LEVEL, "level " + level + " of '" + baseKey
                            + "' has a non-mapping ability entry", block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                // The first block keeps the bare per-level key items already store in PDC; further blocks take
                // /a1, /a2, … dense with no gaps, matching every other multi-ability source and the walk the
                // WornResolver runs over them (crystal/mask/reforge/set/pet).
                String stableKey = index == 0 ? levelKey : levelKey + "/a" + index;
                abilities.add(ability(stableKey, level, block, lvl, root, baseKey, triggers, disabledWorlds, group,
                        repeatTicks, repeatDelayTicks, suppressImmune, nextDefId, diags));
                index++;
            }
            if (index == 0) {
                diags.error(DiagCode.E_LOAD_ENCHANT_LEVEL, "level " + level + " of '" + baseKey
                        + "' declares an empty 'abilities' list", lvl.sourceOf("abilities"),
                        "give it at least one ability entry, or use a direct 'effects:' list");
            }
        }

        EnchantDef def = new EnchantDef(baseKey, display, description == null ? "" : description,
                tier, appliesTo, maxLevel, requires, blacklist, removesRequired, fileSource);
        return new Parsed(def, abilities);
    }

    /**
     * One ability of one level. {@code block} is the {@code abilities:} entry, or {@code null} for the
     * single-block shape where the level node IS the ability — that path must stay byte-identical, since
     * every shipped enchant and every enchanted item in the world depends on it.
     */
    private static AbilityDef ability(String stableKey, int level, YamlNode block, YamlNode lvl, YamlNode root,
                                      String baseKey, List<String> triggers, List<String> disabledWorlds,
                                      String group, int rootRepeatTicks, int rootRepeatDelayTicks,
                                      boolean suppressImmune, IntSupplier nextDefId, Diagnostics diags) {
        ContentParse.Chance chance =
                ContentParse.resolveChanceValue(knobNode(block, lvl, root, "chance"), "chance", diags);
        int cooldown = ContentParse.resolveInt(knobNode(block, lvl, root, "cooldown"), "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(knobNode(block, lvl, root, "soul-cost"), "soul-cost", 0, diags);
        double soulCostGrowth = ContentParse.resolveDouble(
                knobNode(block, lvl, root, "soul-cost-growth"), "soul-cost-growth", 1.0, diags);
        int soulCostCap = ContentParse.resolveInt(
                knobNode(block, lvl, root, "soul-cost-cap"), "soul-cost-cap", 0, diags);
        int soulCostDecayPeriod = ContentParse.resolveInt(
                knobNode(block, lvl, root, "soul-cost-decay-period"), "soul-cost-decay-period", 0, diags);
        String noSoulsMessage = ContentParse.blankToNull(ContentParse.resolveString(
                knobNode(block, lvl, root, "no-souls-message"), "no-souls-message", diags));
        // Each soul knob keeps its OWN innermost-declaring scope, so the three resolve off three nodes.
        ContentParse.SoulKnobs soulKnobs = ContentParse.resolveSoulKnobs(
                knobNode(block, lvl, root, "soul-cost-carried"),
                knobNode(block, lvl, root, "no-souls-sound"),
                knobNode(block, lvl, root, "no-souls-particle"), diags);
        String cdScopeEnchant = ContentParse.resolveCooldownScope(
                knobNode(block, lvl, root, "cooldown-scope"), baseKey, diags);
        boolean cooldownPerVictim = ContentParse.resolveCooldownPerVictim(
                knobNode(block, lvl, root, "cooldown-per-victim"), diags);
        String condition = ContentParse.blankToNull(
                ContentParse.resolveString(knobNode(block, lvl, root, "condition"), "condition", diags));
        // A block may retarget itself (an ATTACK enchant whose second block rides DEFENSE); absent → the enchant's.
        List<String> blockTriggers = block != null && block.has("trigger") ? block.stringList("trigger") : triggers;
        int repeatTicks = block != null && block.has("repeat")
                ? ContentParse.optInt(block, "repeat", 0, diags) : rootRepeatTicks;
        int repeatDelayTicks = block != null && block.has("repeat-delay")
                ? ContentParse.optInt(block, "repeat-delay", -1, diags) : rootRepeatDelayTicks;
        YamlNode effectsNode = block != null ? block : lvl;
        List<EffectLine> effects = effectsFor(baseKey, level, effectsNode, diags);
        // R-QC40: a block may narrow its OWN IMPACT source scope, so an arm and its payload can be a pair
        // inside a family whose root group everything else shares. Deliberately NOT the root group itself —
        // that stays the suppression match key a family-wide negation names (ADR-0074 §4, amended), which is
        // why this rides sourceGroup and leaves cdScopeGroup alone. The crystal/mask readers have no such
        // split: one authored group per ability, so it is both there.
        String sourceGroup = block != null && block.has("group")
                ? ContentParse.blankToNull(block.string("group")) : null;

        return new AbilityDef(
                SourceKind.ENCHANT,
                stableKey,
                nextDefId.getAsInt(),
                level,
                chance.constant(),
                cooldown,
                soulCost,
                blockTriggers,
                disabledWorlds,
                condition,
                effects,
                baseKey,   // one suppression key: DISABLE_ENCHANT silences every block of the enchant
                cdScopeEnchant, // blocks share the enchant's cooldown bucket unless one opts out
                group,
                null,
                repeatTicks,
                effectsNode.source(),
                0,
                suppressImmune,
                chance.expr(),
                noSoulsMessage,
                soulKnobs.carried(),
                soulKnobs.sound(),
                soulKnobs.particle(),
                soulCostGrowth,
                soulCostCap,
                soulCostDecayPeriod,
                cooldownPerVictim,
                repeatDelayTicks,
                sourceGroup);
    }

    /** The node a knob is read from: the innermost scope that declares it — block, then level, then file root. */
    private static YamlNode knobNode(YamlNode block, YamlNode lvl, YamlNode root, String key) {
        if (block != null && block.has(key)) {
            return block;
        }
        return lvl != null && lvl.has(key) ? lvl : root;
    }

    /**
     * The level's own {@code effects:} list. A MISSING key warns — a no-op ability is almost always a mistake —
     * but an explicitly authored {@code effects: []} loads silently (R-QC59): a level whose whole job is a
     * condition, a cooldown or a lore rung is a deliberate shape, and warning at it trained authors to ignore
     * the code that catches the real omission.
     */
    private static List<EffectLine> effectsFor(String baseKey, int level, YamlNode lvl, Diagnostics diags) {
        List<EffectLine> effects = lvl.has("effects")
                ? ContentParse.effectItems(lvl, "effects", diags)
                : new ArrayList<>();
        if (effects.isEmpty() && !lvl.has("effects")) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "level " + level + " of '" + baseKey + "' declares no effects",
                    lvl.sourceOf("effects"));
        }
        return effects;
    }
}
