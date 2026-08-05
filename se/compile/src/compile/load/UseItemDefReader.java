package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored use-item file into its {@link UseItemDef} plus one or more {@link AbilityDef}s
 * (§3.4, docs/decisions/0048-use-items.md). Modelled on {@link CrystalDefReader}: behaviour lives in an
 * {@code abilities:} list (each block carries {@code chance} / {@code cooldown} / {@code condition} /
 * {@code effects}) OR the single-ability shorthand at the top level; the first ability keys to
 * {@code use:<key>}, further ones to {@code use:<key>/a1}, {@code /a2}, ….
 *
 * <p>The {@code USE} trigger is IMPLICIT — every ability fires on right-click. An authored {@code trigger:} is
 * a {@link DiagCode#W_LOAD_USE_TRIGGER} warning and forced back to {@code USE}. A {@code commands:} list lowers
 * into {@code RUN_COMMAND} effect nodes appended to ability a0 (after the authored effects), so a use-item can
 * both fire engine effects and run console/player commands from the one right-click. A bad field is
 * warned-and-skipped, never thrown; a missing name or material skips the whole def.
 */
final class UseItemDefReader {

    private static final Set<String> ROOT_KEYS = ContentParse.withCooldownScope(
            "name", "material", "consumable", "is-food", "shiny", "lore", "permission", "commands", "abilities",
            // single-ability shorthand (a use-item with exactly one ability authors these at the top level):
            "trigger", "chance", "cooldown", "condition", "effects");
    private static final Set<String> ABILITY_KEYS = ContentParse.withCooldownScope(
            "trigger", "chance", "cooldown", "condition", "effects");

    private UseItemDefReader() {
    }

    record Parsed(UseItemDef def, List<AbilityDef> abilities) {
    }

    static Parsed read(String key, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LOAD_USE_ITEM, "use-item file '" + key + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String name = ContentParse.blankToNull(root.string("name"));
        String material = ContentParse.blankToNull(root.string("material"));
        if (name == null || material == null) {
            diags.error(DiagCode.E_LOAD_USE_ITEM,
                    "use-item '" + key + "' must declare both a name and a material", fileSource);
            return new Parsed(null, List.of());
        }
        List<String> lore = root.stringList("lore");
        boolean consumable = ContentParse.boolOr(root.string("consumable"), true, "consumable",
                DiagCode.W_LOAD_BOOL, root.sourceOf("consumable"), diags);
        boolean isFood = ContentParse.boolOr(root.string("is-food"), false, "is-food",
                DiagCode.W_LOAD_BOOL, root.sourceOf("is-food"), diags);
        boolean shiny = ContentParse.boolOr(root.string("shiny"), false, "shiny",
                DiagCode.W_LOAD_BOOL, root.sourceOf("shiny"), diags);
        String permission = orEmpty(ContentParse.blankToNull(root.string("permission")));

        // The commands: list always lowers onto ability a0 (appended after its authored effects), regardless of
        // whether the behaviour is authored in the shorthand or the abilities-list form.
        List<EffectLine> commandEffects = lowerCommands(root, diags);

        List<AbilityDef> abilities = new ArrayList<>();
        List<String> stableKeys = new ArrayList<>();
        List<String> conditionSources = new ArrayList<>();
        if (root.has("abilities")) {
            // A top-level trigger sits alongside the per-block ones; warn here since ability() only sees the blocks.
            if (!root.stringList("trigger").isEmpty()) {
                diags.warning(DiagCode.W_LOAD_USE_TRIGGER, "use-item '" + key
                        + "' declares a top-level trigger; the USE trigger is implicit and the authored value is ignored",
                        root.sourceOf("trigger"));
            }
            int index = 0;
            for (YamlNode block : root.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_USE_ITEM, "use-item '" + key + "' has a non-mapping ability entry",
                            block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                String stableKey = index == 0 ? "use:" + key : "use:" + key + "/a" + index;
                abilities.add(ability(stableKey, block, index == 0 ? commandEffects : List.of(),
                        fileSource, nextDefId, conditionSources, diags));
                stableKeys.add(stableKey);
                index++;
            }
            if (abilities.isEmpty()) {
                diags.error(DiagCode.E_LOAD_USE_ITEM, "use-item '" + key + "' declares an empty 'abilities' list",
                        root.sourceOf("abilities"));
            }
        } else {
            String stableKey = "use:" + key;
            abilities.add(ability(stableKey, root, commandEffects, fileSource, nextDefId, conditionSources, diags));
            stableKeys.add(stableKey);
        }

        // Lore {TIME_FORMATTED} renders a0's cooldown, so a def with no abilities carries a zero cooldown.
        int cooldownTicks = abilities.isEmpty() ? 0 : abilities.get(0).cooldownTicks();
        UseItemDef def = new UseItemDef(key, name, material, lore, consumable, isFood, shiny, permission,
                cooldownTicks, conditionSources, stableKeys);
        return new Parsed(def, abilities);
    }

    private static AbilityDef ability(String stableKey, YamlNode node, List<EffectLine> extraEffects,
                                      Source fileSource, IntSupplier nextDefId, List<String> conditionSources,
                                      Diagnostics diags) {
        // USE is implicit; an authored trigger is warned and forced back so a use-item can only ever fire on use.
        if (!node.stringList("trigger").isEmpty()) {
            diags.warning(DiagCode.W_LOAD_USE_TRIGGER, "use-item ability '" + stableKey
                    + "' declares a trigger; the USE trigger is implicit and the authored value is ignored",
                    node.sourceOf("trigger"));
        }
        ContentParse.Chance chance = ContentParse.resolveChanceValue(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        conditionSources.add(orEmpty(condition));

        List<EffectLine> effects = new ArrayList<>(ContentParse.effectItems(node, "effects", diags));
        effects.addAll(extraEffects);
        if (effects.isEmpty() && !node.has("effects")) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "use-item ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return new AbilityDef(
                SourceKind.USE_ITEM, stableKey, nextDefId.getAsInt(), 0, chance.constant(), cooldown, 0, List.of("USE"),
                List.of(), condition, effects, stableKey,
                ContentParse.resolveCooldownScope(node, stableKey, diags),
                null, null, 0, fileSource, 0, false,
                // no-souls-message and the rest of the soul envelope are deliberately absent: a use-item's soul
                // cost is hard-zero, so gate 10 can never charge or abort one and the keys would be dead knobs.
                chance.expr(), null, false, null, null, 1.0, 0, 0,
                ContentParse.resolveCooldownPerVictim(node, diags),
                -1); // a use-item never repeats, so it carries no initial delay
    }

    /**
     * Lower the {@code commands:} list into {@code RUN_COMMAND} effect lines: a bare string runs as console
     * ({@code { RUN_COMMAND: { command: <s>, as: console } }}); a {@code { as: player|console, run: "..." }}
     * map honours its {@code as}. A malformed entry is a warned-and-skipped diagnostic.
     */
    private static List<EffectLine> lowerCommands(YamlNode root, Diagnostics diags) {
        List<EffectLine> out = new ArrayList<>();
        for (YamlNode item : root.items("commands")) {
            if (item.isScalar()) {
                out.add(runCommand(item.scalar(), "console", item.source()));
                continue;
            }
            if (!item.isMapping()) {
                diags.warning(DiagCode.W_LOAD_EFFECTS,
                        "use-item command entry must be a string or a { as:, run: } map", item.source());
                continue;
            }
            String run = ContentParse.blankToNull(item.string("run"));
            if (run == null) {
                diags.warning(DiagCode.W_LOAD_EFFECTS,
                        "use-item command map declares no 'run' string", item.source());
                continue;
            }
            String as = orDefault(ContentParse.blankToNull(item.string("as")), "console");
            out.add(runCommand(run, as, item.source()));
        }
        return out;
    }

    private static EffectLine runCommand(String command, String as, Source source) {
        LinkedHashMap<String, String> named = new LinkedHashMap<>();
        named.put("command", command);
        named.put("as", as);
        return EffectLine.verbose("RUN_COMMAND", 1, named, null, source);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
