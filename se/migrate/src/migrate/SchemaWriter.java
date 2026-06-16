package migrate;

import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;
import migrate.model.MigratedSet;

/**
 * Renders a {@link MigratedEnchant}/{@link MigratedSet} to StarEnchants YAML (docs/architecture.md §10)
 * — valid, loadable content for everything that mapped, with the original legacy token kept as a
 * trailing comment and anything unmapped emitted as a {@code # TODO port manually} line (never a
 * silently-wrong value). The output is meant to be reviewed before shipping, but the mapped portion
 * compiles as-is through the production loader.
 */
public final class SchemaWriter {

    private SchemaWriter() {
    }

    /** Render one migrated enchant to a StarEnchants enchant YAML document. */
    public static String enchant(MigratedEnchant e) {
        StringBuilder b = new StringBuilder();
        b.append("# Imported from EliteEnchantments (id: ").append(e.id()).append("). Review before shipping.\n");
        b.append("display: ").append(q(e.display())).append('\n');
        if (!e.description().isBlank()) {
            b.append("description: ").append(q(e.description())).append('\n');
        }
        if (e.trigger() != null) {
            b.append("trigger: ").append(e.trigger()).append('\n');
        } else {
            b.append("# TODO set a trigger — legacy type '").append(e.legacyTrigger())
                    .append("' has no StarEnchants equivalent\n");
        }
        if (!e.appliesTo().isEmpty()) {
            b.append("applies-to: [").append(String.join(", ", e.appliesTo())).append("]\n");
        } else if (e.legacyApplies() != null) {
            b.append("# TODO set applies-to — legacy applies '").append(e.legacyApplies()).append("' was not recognised\n");
        }
        b.append("group: ").append(q(e.group())).append('\n'); // quoted: legacy group is free text
        if (e.levels().isEmpty()) {
            b.append("# TODO no levels migrated — add at least one level (an enchant requires one)\n");
        }
        b.append("levels:\n");
        for (MigratedLevel level : e.levels()) {
            b.append("  ").append(level.level()).append(":\n");
            if (level.chance() != null) {
                b.append("    chance: ").append(trimNumber(level.chance())).append('\n');
            }
            if (level.cooldown() != null) {
                b.append("    cooldown: ").append(level.cooldown()).append('\n');
            }
            if (level.condition() != null) {
                b.append("    condition: ").append(q(level.condition())).append('\n');
            }
            appendEffects(b, level.effects(), "    "); // nested under "  <level>:"
        }
        return b.toString();
    }

    /** Render one migrated armour set to a StarEnchants set YAML document. */
    public static String set(MigratedSet s) {
        StringBuilder b = new StringBuilder();
        b.append("# Imported from EliteArmor (id: ").append(s.id())
                .append("). DEFENSE-triggered; review before shipping.\n");
        b.append("display: ").append(q(s.display())).append('\n');
        b.append("trigger: DEFENSE\n");
        b.append("applies-to: [").append(String.join(", ", s.pieces())).append("]\n");
        b.append("pieces: ").append(s.threshold()).append('\n');
        b.append("chance: 100\n");
        appendEffects(b, s.effects(), ""); // set effects sit at the document root
        return b.toString();
    }

    /**
     * Append the {@code effects:} block at the given key indent: a quoted list entry per mapped effect,
     * a {@code # TODO} comment per unmapped one. List items / comments are indented two spaces deeper.
     */
    private static void appendEffects(StringBuilder b, java.util.List<MigratedEffect> effects, String keyIndent) {
        String itemIndent = keyIndent + "  ";
        b.append(keyIndent).append("effects:");
        boolean anyMapped = effects.stream().anyMatch(MigratedEffect::mapped);
        b.append(anyMapped ? "\n" : " []\n"); // an empty list keeps the YAML valid when all effects are TODOs
        for (MigratedEffect effect : effects) {
            if (effect.mapped()) {
                b.append(itemIndent).append("- ").append(q(effect.se()));
                b.append("  # from ").append(effect.legacy());
                if (!effect.note().isBlank()) {
                    b.append(" — ").append(effect.note());
                }
                b.append('\n');
            } else {
                b.append(itemIndent).append("# TODO port manually: ").append(effect.legacy())
                        .append(" — ").append(effect.note()).append('\n');
            }
        }
    }

    /** A whole-number double renders without a trailing {@code .0} so {@code chance: 15}, not {@code 15.0}. */
    private static String trimNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /**
     * A YAML double-quoted scalar (color codes and colons are safe inside). Escapes backslash and quote,
     * plus the control characters a raw legacy string can carry (newline/tab/CR) — an unescaped LF inside
     * a double-quoted scalar would otherwise produce invalid YAML on reload.
     */
    private static String q(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r") + '"';
    }
}
