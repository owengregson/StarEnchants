package migrate;

import java.util.function.Function;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;
import migrate.model.MigratedSet;
import schema.spec.ParamSpec;

/**
 * Renders a {@link MigratedEnchant}/{@link MigratedSet} to StarEnchants content YAML
 * (docs/architecture.md §10; ADR-0016) — valid, loadable content for everything that mapped, with the
 * original legacy token kept as a trailing comment and anything unmapped emitted as a
 * {@code # TODO port manually} line (never a silently-wrong value). The mapped portion compiles as-is
 * through the production loader; the output is meant to be reviewed before shipping.
 *
 * <p>Effects render in the v2 <strong>verbose</strong> form ({@code { HEAD: { param: value, who: … } }})
 * when a {@code specs} lookup (effect head → {@link ParamSpec}) is supplied — so migrated configs are
 * stored in the unified v2 format, not terse. With {@code specs == null} they fall back to the terse
 * quoted string (still valid v2), which keeps the writer usable without the engine's effect registry.
 */
public final class SchemaWriter {

    private SchemaWriter() {
    }

    /** Render one migrated enchant to a v2 enchant YAML document; {@code origin} names the source plugin. */
    public static String enchant(MigratedEnchant e, String origin, Function<String, ParamSpec> specs) {
        StringBuilder b = new StringBuilder();
        b.append("# Imported from ").append(origin).append(" (id: ").append(e.id())
                .append("). Review before shipping.\n");
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
            for (String todo : level.conditionTodos()) {
                b.append("    # TODO condition (port manually): ").append(todo).append('\n');
            }
            appendEffects(b, level.effects(), "    ", specs); // nested under "  <level>:"
        }
        return b.toString();
    }

    /**
     * Render one migrated armour set to a set YAML document (§6.6). EliteArmor sets are armour-only (no
     * weapon member), so the output has just an {@code armor:} block: each piece becomes a member with a
     * default material the operator should confirm, sharing the document's DEFENSE bonus.
     */
    public static String set(MigratedSet s, Function<String, ParamSpec> specs) {
        StringBuilder b = new StringBuilder();
        b.append("# Imported from EliteArmor (id: ").append(s.id())
                .append("). DEFENSE-triggered armour set; confirm the member materials/names before shipping.\n");
        b.append("display: ").append(q(s.display())).append('\n');
        b.append("complete: ").append(s.threshold()).append('\n');
        b.append("armor:\n");
        b.append("  pieces:\n");
        if (s.pieces().isEmpty()) {
            b.append("    # TODO no armour pieces migrated — declare at least one (slot: { material: ... })\n");
        }
        for (String piece : s.pieces()) {
            String slot = piece.toLowerCase(java.util.Locale.ROOT);
            String material = defaultArmorMaterial(slot);
            if (material == null) {
                b.append("    # TODO unrecognised piece '").append(piece)
                        .append("' — add it as 'slot: { material: <MATERIAL> }'\n");
            } else {
                b.append("    ").append(slot).append(": { material: ").append(material)
                        .append(" }  # TODO confirm the armour material; add a name: for a custom item name\n");
            }
        }
        b.append("  trigger: DEFENSE\n");
        b.append("  chance: 100\n");
        appendEffects(b, s.effects(), "  ", specs); // armour bonus effects nested under armor:
        return b.toString();
    }

    /** A sensible default material per armour slot for a migrated set (the operator confirms it). */
    private static String defaultArmorMaterial(String slot) {
        return switch (slot) {
            case "helmet" -> "DIAMOND_HELMET";
            case "chestplate" -> "DIAMOND_CHESTPLATE";
            case "leggings" -> "DIAMOND_LEGGINGS";
            case "boots" -> "DIAMOND_BOOTS";
            default -> null;
        };
    }

    /**
     * Append the {@code effects:} block at the given key indent: a v2 effect item per mapped effect
     * (verbose when {@code specs} resolves the head, else terse), a {@code # TODO} comment per unmapped
     * one. List items / comments are indented two spaces deeper.
     */
    private static void appendEffects(StringBuilder b, java.util.List<MigratedEffect> effects, String keyIndent,
                                      Function<String, ParamSpec> specs) {
        String itemIndent = keyIndent + "  ";
        b.append(keyIndent).append("effects:");
        boolean anyMapped = effects.stream().anyMatch(MigratedEffect::mapped);
        b.append(anyMapped ? "\n" : " []\n"); // an empty list keeps the YAML valid when all effects are TODOs
        for (MigratedEffect effect : effects) {
            if (effect.mapped()) {
                b.append(itemIndent).append("- ").append(V2Effects.item(effect.se(), specs));
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
