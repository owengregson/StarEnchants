package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored crystal file into its {@link CrystalDef} plus exactly ONE {@link AbilityDef}
 * (ADR-0014, ADR-0016). A crystal has no levels; its stable key is the base key an item stores in its
 * crystal list and must NOT carry a {@code /level} suffix. A bad field is warned-and-skipped, never thrown.
 */
final class CrystalDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "material", "name", "lore", "applies-to", "trigger",
            "disabled-worlds", "group", "repeat", "chance", "cooldown", "soul-cost", "condition", "effects");

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
            diags.error("load.crystal", "crystal file '" + baseKey + "' must be a YAML mapping", fileSource);
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
            diags.error("load.crystal.trigger", "crystal '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        List<String> disabledWorlds = root.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(root.string("group"));
        int repeatTicks = ContentParse.optInt(root, "repeat", 0, diags);

        double chance = ContentParse.resolveChance(root, "chance", diags);
        int cooldown = ContentParse.resolveInt(root, "cooldown", 0, diags);
        int soulCost = ContentParse.resolveInt(root, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(root.string("condition"));
        List<EffectLine> effects = ContentParse.effectItems(root, "effects", diags);
        if (effects.isEmpty()) {
            diags.warning("load.effects", "crystal '" + baseKey + "' declares no effects", root.sourceOf("effects"));
        }

        AbilityDef ability = new AbilityDef(
                SourceKind.CRYSTAL,
                baseKey,
                nextDefId.getAsInt(),
                0,
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
                fileSource,
                0);

        String material = ContentParse.blankToNull(root.string("material"));
        String name = ContentParse.blankToNull(root.string("name"));
        List<String> lore = root.stringList("lore");
        CrystalDef def = new CrystalDef(baseKey, display, description == null ? "" : description,
                tier, material, name, lore, appliesTo, fileSource);
        return new Parsed(def, List.of(ability));
    }
}
