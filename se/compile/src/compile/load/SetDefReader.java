package compile.load;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.List;
import java.util.function.IntSupplier;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Reads one authored armour-set file (a composed {@link YamlNode} mapping) into its metadata
 * {@link SetDef} plus exactly ONE {@link AbilityDef} — the set bonus (docs/architecture.md §6.6;
 * ADR-0014). Like a crystal, a set bonus has no levels and its stable key is the path-derived base
 * key itself (e.g. {@code sets/yeti}); unlike a crystal it carries a {@code pieces:} threshold, which
 * is erased onto the ability's {@code setPieces} so the resolver knows how many worn pieces complete
 * the set. An item is stamped with this base key in its {@code CombatState.setKey} to join the set.
 *
 * <p>Every fault is a {@code file:line:col} diagnostic; a missing trigger or a non-positive piece
 * count is a blocking diagnostic (the load stays non-publishable), but the reader still parses the
 * rest for reporting.
 */
final class SetDefReader {

    private SetDefReader() {
    }

    /** One set's parsed output: its metadata and the single bonus ability it expands into. */
    record Parsed(SetDef def, List<AbilityDef> abilities) {
    }

    /** Parse one set. {@code baseKey} is the path-derived key, e.g. {@code sets/yeti}. */
    static Parsed read(String baseKey, YamlNode root, IntSupplier nextDefId, Diagnostics diags) {
        Source fileSource = root.source();
        if (!root.isMapping()) {
            diags.error("load.set", "set file '" + baseKey + "' must be a YAML mapping", fileSource);
            return new Parsed(null, List.of());
        }

        String display = ContentParse.blankToNull(root.string("display"));
        if (display == null) {
            display = baseKey;
        }
        String description = ContentParse.blankToNull(root.string("description"));
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

        double chance = ContentParse.clampChance(
                ContentParse.optDouble(root, "chance", 100.0, diags), root.sourceOf("chance"), diags);
        int cooldown = ContentParse.optInt(root, "cooldown", 0, diags);
        int soulCost = ContentParse.optInt(root, "soul-cost", 0, diags);
        String condition = ContentParse.blankToNull(root.string("condition"));
        List<EffectLine> effects = ContentParse.effectLines(root, "set '" + baseKey + "'", diags);

        AbilityDef ability = new AbilityDef(
                SourceKind.SET,
                baseKey,        // the set's stable key IS the stamped key — no /level suffix
                nextDefId.getAsInt(),
                0,              // level: sets are levelless
                chance,
                cooldown,
                soulCost,
                triggers,
                disabledWorlds,
                condition,
                effects,
                baseKey,        // suppressKey: DISABLE_* cancels by the set identity
                baseKey,        // cdScopeEnchant: per-set cooldown scope
                group,          // cdScopeGroup (may be null)
                null,           // cdScopeType: deferred
                repeatTicks,
                fileSource,
                Math.max(0, pieces)); // setPieces: the completion threshold the resolver gates on

        SetDef def = new SetDef(baseKey, display, description == null ? "" : description,
                Math.max(0, pieces), appliesTo, fileSource);
        return new Parsed(def, List.of(ability));
    }
}
