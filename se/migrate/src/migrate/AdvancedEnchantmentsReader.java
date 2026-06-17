package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;

/**
 * Reads an AdvancedEnchantments {@code enchantments.yml} into {@link MigratedEnchant}s
 * (docs/architecture.md §10). Unlike EliteEnchantments, AE keeps each enchant at the document ROOT
 * (no {@code Enchants:} wrapper), its {@code type}/{@code applies}/effects use AE's own vocabulary
 * (mapped through {@link Mappings} {@code ae*}), and effects carry a space-separated target. AE's
 * condition DSL ({@code conditions: "%victim health% > 5 : %stop%"}) has no v1 equivalent and is NOT
 * carried over — the operator re-adds conditions by hand (the writer's TODO comments flag the gaps).
 * Parses with SnakeYAML directly via {@link LegacyYaml}.
 */
public final class AdvancedEnchantmentsReader {

    private AdvancedEnchantmentsReader() {
    }

    /** Parse the AE {@code enchantments.yml} content into the intermediate model. */
    public static List<MigratedEnchant> read(String yaml) {
        Map<?, ?> root = LegacyYaml.parse(yaml);
        List<MigratedEnchant> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> e)) {
                continue; // a non-mapping top-level node (not an enchant) — skip
            }
            String id = String.valueOf(entry.getKey());
            String legacyType = LegacyYaml.string(e, "type", null);
            List<String> applies = LegacyYaml.stringList(e, "applies");
            out.add(new MigratedEnchant(
                    id,
                    LegacyYaml.string(e, "display", id),
                    String.join(" ", LegacyYaml.stringList(e, "description")), // scalar OR list
                    Mappings.aeTrigger(legacyType),
                    Mappings.aeAppliesTo(applies),
                    LegacyYaml.string(e, "group", "imported").toLowerCase(Locale.ROOT),
                    levels(LegacyYaml.map(e, "levels")),
                    legacyType,
                    applies.isEmpty() ? null : String.join(", ", applies)));
        }
        return out;
    }

    private static List<MigratedLevel> levels(Map<?, ?> levels) {
        List<MigratedLevel> out = new ArrayList<>();
        if (levels == null) {
            return out;
        }
        for (Map.Entry<?, ?> entry : levels.entrySet()) {
            int level;
            try {
                level = Integer.parseInt(String.valueOf(entry.getKey()).trim());
            } catch (NumberFormatException notALevel) {
                continue; // a non-numeric level key — skip it
            }
            if (!(entry.getValue() instanceof Map<?, ?> lvl)) {
                continue;
            }
            List<MigratedEffect> effects = new ArrayList<>();
            for (String token : LegacyYaml.stringList(lvl, "effects")) {
                effects.add(Mappings.aeEffect(token));
            }
            out.add(new MigratedLevel(
                    level,
                    LegacyYaml.doubleOrNull(lvl, "chance"),
                    LegacyYaml.intOrNull(lvl, "cooldown"),
                    null, // AE conditions don't map to the v1 condition DSL — re-add by hand
                    effects));
        }
        return out;
    }
}
