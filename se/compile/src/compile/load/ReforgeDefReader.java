package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored weapon-reforge file into its {@link ReforgeDef} plus one or more {@link AbilityDef}s
 * (ADR-0070). A reforge has no levels and no applies-to (the applies target is the live
 * {@code reforges.weapon-groups} set, not per-def); its stable key is the source-prefixed {@code reforges/<stem>}
 * a reforged weapon stores (the mask rule): the def key, the a0 ability stable key and the weapon's combat-blob
 * value are the SAME string, so no re-namespacing exists.
 *
 * <p>Behaviour lives in an {@code abilities:} list — the crystal dual form — or the top-level shorthand for a
 * single-ability reforge. The first ability (a0) is the ACTIVE: its {@code USE} trigger is IMPLICIT (an authored
 * {@code trigger:} is a {@link DiagCode#W_LOAD_REFORGE_TRIGGER} warning, forced back to {@code USE}); support
 * abilities (a1+) may author any trigger (PASSIVE/REPEATING/…, the mask rule) and an empty trigger list defaults
 * to {@code USE} so the ability joins the activation. A missing display or an ability-less reforge is
 * {@link DiagCode#E_LOAD_REFORGE}; a bad field is warned-and-skipped, never thrown.
 */
final class ReforgeDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "color", "icon", "material", "type", "description", "abilities",
            // single-ability shorthand (a reforge whose whole behaviour is the active):
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");
    private static final Set<String> ABILITY_KEYS = Set.of(
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");

    private ReforgeDefReader() {
    }

    record Parsed(ReforgeDef def, List<AbilityDef> abilities) {
    }

    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LOAD_REFORGE, "reforge file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        // A reforge REQUIRES a display — its {NAME} feeds the universal likeness and the on-weapon line, so a
        // nameless reforge is malformed (the mask rule, not the crystal fallback).
        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            diags.error(DiagCode.E_LOAD_REFORGE, "reforge '" + baseKey + "' must declare a display", fileSource);
            return new Parsed(null, List.of());
        }
        String color = orDefault(ContentParse.blankToNull(root.string("color")), "&f");
        // The icon is the cosmetic catalogue material; authors think in either word (icon: or material:).
        String iconRaw = ContentParse.blankToNull(root.string("icon"));
        if (iconRaw == null) {
            iconRaw = ContentParse.blankToNull(root.string("material"));
        }
        String icon = orDefault(iconRaw, "ANVIL");
        // ACTIVE|PASSIVE — the pets `type:` convention, the likeness {TYPE} header word. Unknown values warn
        // back to ACTIVE (every shipped reforge is an active) rather than fail the def.
        String type = orDefault(ContentParse.blankToNull(root.string("type")), "ACTIVE").toUpperCase(java.util.Locale.ROOT);
        if (!type.equals("ACTIVE") && !type.equals("PASSIVE")) {
            diags.warning(DiagCode.W_LOAD_REFORGE_TRIGGER, "reforge '" + baseKey + "' declares type '" + type
                    + "' — expected ACTIVE or PASSIVE; treated as ACTIVE", root.sourceOf("type"));
            type = "ACTIVE";
        }
        List<String> description = root.stringList("description");

        // Behaviours: the unified abilities list (or the top-level shorthand for a single-ability reforge). The
        // first ability keys to baseKey; further ones to baseKey/a1, /a2, … — dense, no gaps — resolved by the
        // WornResolver /aN walk exactly like a mask's extra abilities.
        List<AbilityDef> abilities = new ArrayList<>();
        List<String> useStableKeys = new ArrayList<>();
        List<String> conditionSources = new ArrayList<>();
        if (root.has("abilities")) {
            int index = 0;
            for (YamlNode block : root.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_REFORGE, "reforge '" + baseKey + "' has a non-mapping ability entry",
                            block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                String stableKey = index == 0 ? baseKey : baseKey + "/a" + index;
                AbilityDef ability = ability(stableKey, index == 0, block, fileSource, nextDefId,
                        conditionSources, diags);
                abilities.add(ability);
                if (ability.triggers().contains("USE")) {
                    useStableKeys.add(stableKey);
                }
                index++;
            }
            if (abilities.isEmpty()) {
                diags.error(DiagCode.E_LOAD_REFORGE, "reforge '" + baseKey + "' declares an empty 'abilities' list",
                        root.sourceOf("abilities"));
                return new Parsed(null, List.of());
            }
        } else {
            AbilityDef ability = ability(baseKey, true, root, fileSource, nextDefId, conditionSources, diags);
            abilities.add(ability);
            if (ability.triggers().contains("USE")) {
                useStableKeys.add(baseKey);
            }
        }

        // Lore {TIME_FORMATTED} renders a0's cooldown (the use-item rule); a def with no abilities carries zero.
        int cooldownTicks = abilities.isEmpty() ? 0 : abilities.get(0).cooldownTicks();
        ReforgeDef def = new ReforgeDef(baseKey, display, color, description, icon, type, cooldownTicks,
                useStableKeys, conditionSources, fileSource);
        return new Parsed(def, abilities);
    }

    /**
     * Read one ability block. {@code active} (the a0 position) forces the implicit {@code USE} trigger; a support
     * ability passes its authored trigger through and an empty trigger list defaults to {@code USE} (it joins the
     * activation). Every USE ability appends its (blank-normalised) condition to {@code conditionSources}, aligned
     * to the reforge's {@code useStableKeys}.
     */
    private static AbilityDef ability(String stableKey, boolean active, YamlNode node, Source fileSource,
                                      IntSupplier nextDefId, List<String> conditionSources, Diagnostics diags) {
        List<String> authored = node.stringList("trigger");
        List<String> triggers;
        if (active) {
            if (!authored.isEmpty()) {
                diags.warning(DiagCode.W_LOAD_REFORGE_TRIGGER, "reforge ability '" + stableKey
                        + "' declares a trigger; the USE trigger is implicit on the reforge active and the authored value is ignored",
                        node.sourceOf("trigger"));
            }
            triggers = List.of("USE");
        } else {
            triggers = authored.isEmpty() ? List.of("USE") : authored;
        }
        List<String> disabledWorlds = node.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(node.string("group"));
        int repeatTicks = ContentParse.optInt(node, "repeat", 0, diags);
        ContentParse.Chance chance = ContentParse.resolveChanceValue(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(node, "effects", diags);
        if (effects.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "reforge ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        if (triggers.contains("USE")) {
            conditionSources.add(orEmpty(condition));
        }
        // Cooldown-scopes to its OWN stable key (ENCHANT scope; gate 6 arms it on the USE path automatically),
        // an authored group overriding the cooldown group — the mask reader shape.
        return new AbilityDef(
                SourceKind.REFORGE, stableKey, nextDefId.getAsInt(), 0, chance.constant(), cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey, stableKey, group, null, repeatTicks, fileSource, 0, false, chance.expr());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
