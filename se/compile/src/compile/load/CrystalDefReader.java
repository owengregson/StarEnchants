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
 * Reads one authored crystal file into its {@link CrystalDef} plus one or more {@link AbilityDef}s (ADR-0014,
 * ADR-0034). A crystal has no levels; its stable key is the base key an item stores in its crystal list and
 * must NOT carry a {@code /level} suffix.
 *
 * <p>Behaviour lives in an {@code abilities:} list — each block carries its own {@code trigger} / {@code chance}
 * / {@code cooldown} / {@code condition} / {@code effects}, exactly like an armour set's {@code bonuses:}, so one
 * crystal holds ANY NUMBER of independent effects across triggers (a Cosmic-Enchants "Armor Crystal" bonus is
 * typically an on-attack damage bump AND a permanent worn potion). The first ability keys to {@code <baseKey>},
 * further ones to {@code <baseKey>/a1}, {@code /a2}, … ({@link WornResolver} walks the {@code /aN} chain). A
 * one-bonus crystal may instead put its {@code trigger} + {@code effects} at the top level (the shorthand form).
 * A bad field is warned-and-skipped, never thrown.
 */
final class CrystalDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "applies-to", "abilities",
            // single-ability shorthand (a crystal with exactly one bonus authors these at the top level):
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");
    private static final Set<String> ABILITY_KEYS = Set.of(
            "trigger", "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");

    private CrystalDefReader() {
    }

    record Parsed(CrystalDef def, List<AbilityDef> abilities) {
    }

    /** Test/convenience entry: no folder-derived tier. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        return read(baseKey, null, root, nextDefId, diags);
    }

    static Parsed read(String baseKey, String folderTier, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LOAD_CRYSTAL, "crystal file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }
        ContentParse.warnUnknownKeys(root, ROOT_KEYS, diags);

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        List<String> description = root.stringList("description");
        String tier = ContentParse.resolveTier(folderTier, root, diags);
        List<String> appliesTo = root.stringList("applies-to");

        // Behaviours: the unified abilities list (or the top-level shorthand for a single-bonus crystal). The
        // first ability keys to baseKey; further ones to baseKey/a1, /a2, … — dense, no gaps — resolved by the
        // WornResolver /aN walk exactly like an armour set's extra armour bonuses.
        List<AbilityDef> abilities = new ArrayList<>();
        if (root.has("abilities")) {
            int index = 0;
            for (YamlNode block : root.items("abilities")) {
                if (!block.isMapping()) {
                    diags.error(DiagCode.E_LOAD_CRYSTAL, "crystal '" + baseKey + "' has a non-mapping ability entry",
                            block.source());
                    continue;
                }
                ContentParse.warnUnknownKeys(block, ABILITY_KEYS, diags);
                String stableKey = index == 0 ? baseKey : baseKey + "/a" + index;
                abilities.add(ability(stableKey, block, fileSource, nextDefId, diags));
                index++;
            }
            if (abilities.isEmpty()) {
                diags.error(DiagCode.E_LOAD_CRYSTAL_TRIGGER, "crystal '" + baseKey + "' declares an empty 'abilities' list",
                        root.sourceOf("abilities"));
            }
        } else {
            abilities.add(ability(baseKey, root, fileSource, nextDefId, diags));
        }

        CrystalDef def = new CrystalDef(baseKey, display, description, tier, appliesTo, fileSource);
        return new Parsed(def, abilities);
    }

    private static AbilityDef ability(String stableKey, YamlNode node, Source fileSource,
                                      IntSupplier nextDefId, Diagnostics diags) {
        List<String> triggers = node.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error(DiagCode.E_LOAD_CRYSTAL_TRIGGER, "crystal ability '" + stableKey + "' declares no trigger",
                    node.sourceOf("trigger"));
        }
        List<String> disabledWorlds = node.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(node.string("group"));
        int repeatTicks = ContentParse.optInt(node, "repeat", 0, diags);
        double chance = ContentParse.resolveChance(node, "chance", diags);
        int cooldown = ContentParse.resolveInt(node, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(node, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(node.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(node, "effects", diags);
        if (effects.isEmpty()) {
            diags.warning(DiagCode.W_LOAD_EFFECTS, "crystal ability '" + stableKey + "' declares no effects",
                    node.sourceOf("effects"));
        }
        return new AbilityDef(
                SourceKind.CRYSTAL, stableKey, nextDefId.getAsInt(), 0, chance, cooldown, soulCost, triggers,
                disabledWorlds, condition, effects, stableKey, stableKey, group, null, repeatTicks, fileSource, 0);
    }
}
