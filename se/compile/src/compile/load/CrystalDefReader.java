package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.List;
import java.util.function.IntSupplier;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored crystal file (a composed {@link YamlNode} mapping) into its metadata
 * {@link CrystalDef} plus exactly ONE {@link AbilityDef} (docs/architecture.md §6.5; ADR-0014).
 * A crystal has no levels — its trigger / chance / cooldown / effects live at the top of the file
 * — and its stable key is the path-derived base key itself (e.g. {@code crystals/jolt}), the key an
 * item stores in its crystal list. {@code WornResolver} looks that key up directly, so it must NOT
 * carry a {@code /level} suffix (unlike enchants).
 *
 * <p>Every fault is a {@code file:line:col} diagnostic; a bad field is warned-and-skipped, never
 * thrown. A missing trigger is a blocking diagnostic (the load stays non-publishable) but the
 * reader still parses the rest for reporting.
 */
final class CrystalDefReader {

    private CrystalDefReader() {
    }

    /** One crystal's parsed output: its metadata and the single ability it expands into. */
    record Parsed(CrystalDef def, List<AbilityDef> abilities) {
    }

    /** Parse one crystal. {@code baseKey} is the path-derived key, e.g. {@code crystals/jolt}. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.crystal", "crystal file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey; // non-fatal: default the display name to the key (absent OR blank)
        }
        String description = ContentParse.blankToNull(root.string("description"));
        List<String> appliesTo = root.stringList("applies-to");
        List<String> triggers = root.stringList("trigger");
        if (triggers.isEmpty()) {
            diags.error("load.crystal.trigger", "crystal '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        List<String> disabledWorlds = root.stringList("disabled-worlds");
        String group = ContentParse.blankToNull(root.string("group"));
        int repeatTicks = ContentParse.optInt(root, "repeat", 0, diags);

        double chance = ContentParse.clampChance(
                ContentParse.optDouble(root, "chance", 100.0, diags), root.sourceOf("chance"), diags);
        int cooldown = ContentParse.optInt(root, "cooldown", 0, diags);
        int soulCost = ContentParse.optInt(root, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(root.string("condition"));
        List<EffectLine> effects = ContentParse.effectLines(root, "crystal '" + baseKey + "'", diags);

        AbilityDef ability = new AbilityDef(
                SourceKind.CRYSTAL,
                baseKey,        // the crystal's stable key IS the stored key — no /level suffix
                nextDefId.getAsInt(),
                0,              // level: crystals are levelless (non-enchant source)
                chance,
                cooldown,
                soulCost,
                triggers,
                disabledWorlds,
                condition,
                effects,
                baseKey,        // suppressKey: DISABLE_* cancels by the crystal identity
                baseKey,        // cdScopeEnchant: per-crystal cooldown scope
                group,          // cdScopeGroup (may be null)
                null,           // cdScopeType: deferred
                repeatTicks,
                fileSource);

        CrystalDef def = new CrystalDef(baseKey, display,
                description == null ? "" : description, appliesTo, fileSource);
        return new Parsed(def, List.of(ability));
    }
}
