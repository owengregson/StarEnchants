package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored pet file into its {@link PetDef} plus the {@link AbilityDef}s of every level bracket
 * (ADR-0052). The {@code levels:} map is SPARSE — each key is a level FLOOR (the enchant per-level map, but
 * with gaps); the highest floor at or below the item's stored level is the live bracket. Ability blocks use
 * the crystal dual form (a bracket with one ability may author {@code effects:} etc. directly; otherwise an
 * {@code abilities:} list).
 *
 * <p>Trigger rules per {@code type}: an ACTIVE pet's ability defaults to the implicit {@code USE} (fired by
 * the right-click cold path); its non-{@code USE} abilities are the armed-window ones. A PASSIVE pet's ability
 * defaults to {@code PASSIVE}; authoring {@code USE} on one is a {@link DiagCode#W_LOAD_PET_TRIGGER} warning
 * and forced to {@code PASSIVE} (nothing right-clicks a passive). The bracket's first USE ability carries the
 * pet-wide cooldown scope {@code pet:<key>} so the cooldown survives a bracket crossing; every other ability
 * cools down (if authored) under its own stable key.
 */
final class PetDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "color", "type", "head", "material", "descriptor", "description", "permission", "levels");
    private static final Set<String> ABILITY_KEYS = Set.of(
            "trigger", "disabled-worlds", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");
    private static final Set<String> BRACKET_KEYS = Set.of(
            "cooldown", "duration", "abilities",
            // single-ability shorthand (a bracket with exactly one ability authors these at the top level):
            "trigger", "disabled-worlds", "repeat", "chance", "soul-cost", "condition", "effects");

    private PetDefReader() {
    }

    record Parsed(PetDef def, List<AbilityDef> abilities) {
    }

    static Parsed read(String key, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LOAD_PET, "pet file '" + key + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        String type = ContentParse.blankToNull(root.string("type"));
        if (display == null || type == null) {
            diags.error(DiagCode.E_LOAD_PET,
                    "pet '" + key + "' must declare both a display and a type (ACTIVE|PASSIVE)", fileSource);
            return new Parsed(null, List.of());
        }
        boolean active;
        switch (type.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> active = true;
            case "PASSIVE" -> active = false;
            default -> {
                diags.error(DiagCode.E_LOAD_PET,
                        "pet '" + key + "' type must be ACTIVE or PASSIVE, not '" + type + "'",
                        root.sourceOf("type"));
                return new Parsed(null, List.of());
            }
        }
        String color = orDefault(ContentParse.blankToNull(root.string("color")), "&f");
        String head = orEmpty(ContentParse.blankToNull(root.string("head")));
        String material = orDefault(ContentParse.blankToNull(root.string("material")), "PLAYER_HEAD");
        List<String> descriptor = linesOf(root, "descriptor");
        List<String> description = root.stringList("description");
        String permission = orEmpty(ContentParse.blankToNull(root.string("permission")));

        YamlNode levels = root.child("levels");
        if (levels == null || !levels.isMapping()) {
            diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' must declare a 'levels' bracket map", fileSource);
            return new Parsed(null, List.of());
        }

        // Floors parse into a TreeMap so brackets come out ascending regardless of authored order.
        TreeMap<Integer, YamlNode> byFloor = new TreeMap<>();
        for (YamlNode.Entry entry : levels.entries()) {
            Integer floor = parseFloor(entry.key());
            if (floor == null || floor < 1) {
                diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' has a non-numeric or sub-1 level floor '"
                        + entry.key() + "'", levels.sourceOf(entry.key()));
                continue;
            }
            byFloor.put(floor, entry.value());
        }
        if (byFloor.isEmpty()) {
            diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' declares an empty 'levels' map",
                    root.sourceOf("levels"));
            return new Parsed(null, List.of());
        }

        List<AbilityDef> abilities = new ArrayList<>();
        List<PetBracket> brackets = new ArrayList<>();
        for (var entry : byFloor.entrySet()) {
            PetBracket bracket = readBracket(key, active, entry.getKey(), entry.getValue(), abilities,
                    nextDefId, diags);
            if (bracket != null) {
                brackets.add(bracket);
            }
        }
        if (brackets.isEmpty()) {
            diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' has no usable level bracket",
                    root.sourceOf("levels"));
            return new Parsed(null, List.of());
        }

        PetDef def = new PetDef(key, display, color, active, head, material, descriptor, description, permission,
                brackets);
        return new Parsed(def, abilities);
    }

    /** A field that may be ONE string or a list of lines (the descriptor is usually a single wrapped line). */
    private static List<String> linesOf(YamlNode root, String key) {
        List<String> lines = root.stringList(key);
        if (!lines.isEmpty()) {
            return lines;
        }
        String single = ContentParse.blankToNull(root.string(key));
        return single == null ? List.of() : List.of(single);
    }

    /** Read one bracket (dual form) and append its {@link AbilityDef}s; {@code null} for a malformed block. */
    private static PetBracket readBracket(String key, boolean active, int floor, YamlNode node,
                                          List<AbilityDef> out, IntSupplier nextDefId, Diagnostics diags) {
        if (node == null || !node.isMapping()) {
            diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' level " + floor + " must be a YAML mapping",
                    node == null ? null : node.source());
            return null;
        }
        ContentParse.warnUnknownKeys(node, BRACKET_KEYS, diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int duration = ContentParse.resolveInt(node, "duration", 0, diags);

        List<String> useKeys = new ArrayList<>();
        List<String> wornKeys = new ArrayList<>();
        List<String> conditionSources = new ArrayList<>();
        int firstUseCooldown = -1; // {TIME_FORMATTED} renders a0's ACTUAL cooldown (the use-item rule)
        if (node.has("abilities")) {
            int index = 0;
            for (YamlNode block : node.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' level " + floor
                            + " has a non-mapping ability entry", block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                AbilityDef ability = readAbility(key, active, floor, index, block, cooldown, useKeys, wornKeys,
                        conditionSources, out, nextDefId, diags);
                if (firstUseCooldown < 0 && useKeys.size() == 1 && ability.stableKey().equals(
                        useKeys.get(0))) {
                    firstUseCooldown = ability.cooldownTicks();
                }
                index++;
            }
            if (useKeys.isEmpty() && wornKeys.isEmpty()) {
                diags.error(DiagCode.E_LOAD_PET, "pet '" + key + "' level " + floor
                        + " declares an empty 'abilities' list", node.sourceOf("abilities"));
                return null;
            }
        } else {
            AbilityDef ability = readAbility(key, active, floor, 0, node, cooldown, useKeys, wornKeys,
                    conditionSources, out, nextDefId, diags);
            if (!useKeys.isEmpty()) {
                firstUseCooldown = ability.cooldownTicks();
            }
        }
        return new PetBracket(floor, firstUseCooldown >= 0 ? firstUseCooldown : cooldown, duration,
                useKeys, wornKeys, conditionSources);
    }

    /** Read one ability block, classify it USE vs worn by trigger, and append + return its {@link AbilityDef}. */
    private static AbilityDef readAbility(String key, boolean active, int floor, int index, YamlNode node,
                                          int bracketCooldown, List<String> useKeys, List<String> wornKeys,
                                          List<String> conditionSources, List<AbilityDef> out,
                                          IntSupplier nextDefId, Diagnostics diags) {
        String stableKey = index == 0 ? "pet:" + key + "/" + floor : "pet:" + key + "/" + floor + "/a" + index;
        String trigger = resolveTrigger(key, active, stableKey, node, diags);
        boolean use = "USE".equals(trigger);

        double chance = ContentParse.resolveChance(node, "chance", diags);
        // A USE ability inherits the bracket cooldown unless it authors its own; worn abilities default to none.
        int cooldown = ContentParse.resolveInt(node, "cooldown", use ? bracketCooldown : 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        int repeatTicks = ContentParse.optInt(node, "repeat", 0, diags);
        List<String> disabledWorlds = node.stringList("disabled-worlds");
        String condition = ContentParse.blankToNull(node.string("condition"));

        List<EffectLine> effects = new ArrayList<>(ContentParse.effectItems(node, "effects", diags));
        if (effects.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "pet ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        if (use) {
            useKeys.add(stableKey);
            conditionSources.add(orEmpty(condition));
        } else {
            wornKeys.add(stableKey);
        }
        // The bracket's FIRST USE ability cools down under the pet-wide scope so a level-up that crosses a
        // bracket floor keeps the running cooldown; everything else scopes to its own stable key.
        String cdScope = use && useKeys.size() == 1 ? "pet:" + key : stableKey;
        AbilityDef ability = new AbilityDef(
                SourceKind.PET, stableKey, nextDefId.getAsInt(), 0, chance, cooldown, soulCost,
                List.of(trigger), disabledWorlds, condition, effects, "pet:" + key, cdScope, null, null,
                repeatTicks, node.source(), 0);
        out.add(ability);
        return ability;
    }

    /**
     * The ability's trigger under the ADR-0052 rules: ACTIVE defaults to {@code USE} and may author any other
     * trigger (the armed-window abilities); PASSIVE defaults to {@code PASSIVE} and may author anything BUT
     * {@code USE} (warned and forced back — nothing right-clicks a passive pet).
     */
    private static String resolveTrigger(String key, boolean active, String stableKey, YamlNode node,
                                         Diagnostics diags) {
        List<String> authored = node.stringList("trigger");
        String value = authored.isEmpty() ? null : authored.get(0).toUpperCase(Locale.ROOT);
        if (value == null) {
            return active ? "USE" : "PASSIVE";
        }
        if (!active && "USE".equals(value)) {
            diags.warning(DiagCode.W_LOAD_PET_TRIGGER, "pet ability '" + stableKey
                    + "' authors trigger USE on a PASSIVE pet; forced to PASSIVE", node.sourceOf("trigger"));
            return "PASSIVE";
        }
        return value;
    }

    private static Integer parseFloor(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
