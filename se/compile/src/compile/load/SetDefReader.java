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
 * Reads one authored armour-set file (a composed {@link YamlNode} mapping) into its metadata
 * {@link SetDef} plus exactly ONE {@link AbilityDef} — the set bonus (docs/architecture.md §6.6;
 * ADR-0014, ADR-0016). Like a crystal, a set bonus has no levels and its stable key is the
 * path-derived base key itself (e.g. {@code sets/yeti}); unlike a crystal it carries a {@code pieces:}
 * threshold erased onto the ability's {@code setPieces}.
 *
 * <p>Effects may be terse strings or verbose {@code HEAD: { … }} maps (ADR-0016). Every fault is a
 * {@code file:line:col} diagnostic; a missing trigger or a non-positive piece count is blocking, but
 * the reader still parses the rest for reporting.
 */
final class SetDefReader {

    private static final Set<String> ROOT_KEYS = Set.of(
            "display", "description", "tier", "applies-to", "trigger", "disabled-worlds", "group",
            "repeat", "pieces", "chance", "cooldown", "soul-cost", "condition", "effects");

    private SetDefReader() {
    }

    /** One set's parsed output: its metadata and the single bonus ability it expands into. */
    record Parsed(SetDef def, List<AbilityDef> abilities) {
    }

    /** Test/convenience entry: no folder-derived tier. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        return read(baseKey, null, root, nextDefId, diags);
    }

    /** Parse one set. {@code baseKey} is the path-derived key, e.g. {@code sets/yeti}. */
    static Parsed read(String baseKey, String folderTier, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.set", "set file '" + baseKey + "' must be a YAML mapping", fileSource);
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
            diags.error("load.set.trigger", "set '" + baseKey + "' declares no trigger",
                    root.sourceOf("trigger"));
        }
        int pieces = ContentParse.optInt(root, "pieces", 0, diags);
        if (pieces < 1) {
            diags.error("load.set.pieces", "set '" + baseKey + "' must declare a positive 'pieces' count, got "
                    + pieces, root.sourceOf("pieces"));
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
            diags.warning("load.effects", "set '" + baseKey + "' declares no effects", root.sourceOf("effects"));
        }

        AbilityDef ability = new AbilityDef(
                SourceKind.SET,
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
                Math.max(0, pieces));

        SetDef def = new SetDef(baseKey, display, description == null ? "" : description,
                tier, Math.max(0, pieces), appliesTo, fileSource);
        return new Parsed(def, List.of(ability));
    }
}
