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
 * Reads one authored mask file into its {@link MaskDef} plus one or more {@link AbilityDef}s (ADR-0014,
 * ADR-0053). A mask has no levels and no applies-to (helmets-only by construction); its stable key is the base
 * key a masked helmet stores and must NOT carry a {@code /level} suffix.
 *
 * <p>Behaviour lives in an {@code abilities:} list — each block carries its own {@code trigger} / {@code chance}
 * / {@code cooldown} / {@code condition} / {@code effects}, the crystal dual form, so one mask holds ANY NUMBER
 * of independent effects across triggers (Chef suppresses on ATTACK and DEFENSE at once). The first ability keys
 * to {@code <baseKey>}, further ones to {@code <baseKey>/a1}, {@code /a2}, … ({@link WornResolver} walks the
 * {@code /aN} chain). A one-ability mask may instead put its {@code trigger} + {@code effects} at the top level
 * (the shorthand form). A bad field is warned-and-skipped, never thrown; a missing display or an ability-less
 * mask is {@link DiagCode#E_LOAD_MASK}.
 */
final class MaskDefReader {

    private static final Set<String> ROOT_KEYS = ContentParse.withSoulKnobs(
            "display", "color", "head", "material", "description", "abilities",
            // single-ability shorthand (a mask with exactly one ability authors these at the top level):
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "no-souls-message",
            "condition", "effects");
    private static final Set<String> ABILITY_KEYS = ContentParse.withSoulKnobs(
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "no-souls-message",
            "condition", "effects");

    private MaskDefReader() {
    }

    record Parsed(MaskDef def, List<AbilityDef> abilities) {
    }

    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LOAD_MASK, "mask file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        // Unlike a crystal (which falls back to the base key), a mask REQUIRES a display — its {NAME} feeds the
        // universal likeness and the worn line, so a nameless mask is malformed, not silently keyed.
        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            diags.error(DiagCode.E_LOAD_MASK, "mask '" + baseKey + "' must declare a display", fileSource);
            return new Parsed(null, List.of());
        }
        String color = orDefault(ContentParse.blankToNull(root.string("color")), "&f");
        String head = orEmpty(ContentParse.blankToNull(root.string("head")));
        String material = orDefault(ContentParse.blankToNull(root.string("material")), "PLAYER_HEAD");
        List<String> description = root.stringList("description");

        // Behaviours: the unified abilities list (or the top-level shorthand for a single-ability mask). The
        // first ability keys to baseKey; further ones to baseKey/a1, /a2, … — dense, no gaps — resolved by the
        // WornResolver /aN walk exactly like a crystal's extra bonuses.
        List<AbilityDef> abilities = new ArrayList<>();
        if (root.has("abilities")) {
            int index = 0;
            for (YamlNode block : root.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_MASK, "mask '" + baseKey + "' has a non-mapping ability entry",
                            block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                String stableKey = index == 0 ? baseKey : baseKey + "/a" + index;
                abilities.add(ability(stableKey, block, fileSource, nextDefId, diags));
                index++;
            }
            if (abilities.isEmpty()) {
                diags.error(DiagCode.E_LOAD_MASK, "mask '" + baseKey + "' declares an empty 'abilities' list",
                        root.sourceOf("abilities"));
                return new Parsed(null, List.of());
            }
        } else {
            abilities.add(ability(baseKey, root, fileSource, nextDefId, diags));
        }

        MaskDef def = new MaskDef(baseKey, display, color, description, head, material, fileSource);
        return new Parsed(def, abilities);
    }

    private static AbilityDef ability(String stableKey, YamlNode node, Source fileSource,
                                      IntSupplier nextDefId, Diagnostics diags) {
        List<String> triggers = node.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_MASK, "mask ability '" + stableKey + "' declares no trigger",
                    node.sourceOf("trigger"));
        }
        List<String> disabledWorlds = node.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(node.string("group"));
        int repeatTicks = ContentParse.optInt(node, "repeat", 0, diags);
        ContentParse.Chance chance = ContentParse.resolveChanceValue(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        String noSoulsMessage = ContentParse.blankToNull(
                ContentParse.resolveString(node, "no-souls-message", diags));
        ContentParse.SoulKnobs soulKnobs = ContentParse.resolveSoulKnobs(node, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(node, "effects", diags);
        if (effects.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "mask ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        // Each ability cooldown-scopes to its OWN stable key (no family-wide scope — masks have no USE path),
        // mirroring the crystal reader; an authored group overrides the cooldown group.
        return new AbilityDef(
                SourceKind.MASK, stableKey, nextDefId.getAsInt(), 0, chance.constant(), cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey, stableKey, group, null, repeatTicks, fileSource, 0, false,
                chance.expr(), noSoulsMessage, soulKnobs.carried(), soulKnobs.sound(), soulKnobs.particle());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
