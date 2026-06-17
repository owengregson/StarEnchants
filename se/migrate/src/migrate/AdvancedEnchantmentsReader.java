package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import migrate.model.MigratedCondition;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;

/**
 * Reads an AdvancedEnchantments {@code enchantments.yml} into {@link MigratedEnchant}s
 * (docs/architecture.md §10). Unlike EliteEnchantments, AE keeps each enchant at the document ROOT
 * (no {@code Enchants:} wrapper), its {@code type}/{@code applies}/effects use AE's own vocabulary
 * (mapped through {@link Mappings} {@code ae*}), and effects carry a space-separated target. AE's
 * condition DSL ({@code conditions: "%victim health% > 5 : %stop%"}) is translated where it maps to a
 * StarEnchants allow-gate (an {@code %allow%}/{@code %continue%} or negated {@code %stop%} result over
 * mappable variables); a {@code %force%}/{@code %chance%} result or an unmappable variable becomes a
 * {@code # TODO} comment, never a silently-wrong gate. Parses with SnakeYAML directly via {@link LegacyYaml}.
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
            // AE @Victim/@Attacker name opposite entities on DEFENSE vs ATTACK; the effect/selector mapping
            // must know the direction, derived from the enchant's mapped trigger.
            boolean defenseDirection = "DEFENSE".equals(Mappings.aeTrigger(legacyType));
            out.add(new MigratedEnchant(
                    id,
                    LegacyYaml.string(e, "display", id),
                    String.join(" ", LegacyYaml.stringList(e, "description")), // scalar OR list
                    Mappings.aeTrigger(legacyType),
                    Mappings.aeAppliesTo(applies),
                    LegacyYaml.string(e, "group", "imported").toLowerCase(Locale.ROOT),
                    levels(LegacyYaml.map(e, "levels"), defenseDirection),
                    legacyType,
                    applies.isEmpty() ? null : String.join(", ", applies)));
        }
        return out;
    }

    private static List<MigratedLevel> levels(Map<?, ?> levels, boolean defenseDirection) {
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
                effects.add(Mappings.aeEffect(token, defenseDirection));
            }
            // AE conditions are per-level (a scalar or a list of `LEFT : RESULT` lines). Map each to a
            // StarEnchants allow-gate; combine the mappable lines with `&&`, flag the rest as TODOs.
            List<String> mappedExprs = new ArrayList<>();
            List<String> conditionTodos = new ArrayList<>();
            for (String condLine : LegacyYaml.stringList(lvl, "conditions")) {
                MigratedCondition condition = Mappings.aeCondition(condLine);
                if (condition.mapped()) {
                    mappedExprs.add(condition.expr());
                } else {
                    conditionTodos.add(condition.todo());
                }
            }
            String condition = mappedExprs.isEmpty() ? null
                    : mappedExprs.size() == 1 ? mappedExprs.get(0)
                    : mappedExprs.stream().map(e -> "(" + e + ")").collect(Collectors.joining(" && "));
            out.add(new MigratedLevel(
                    level,
                    LegacyYaml.doubleOrNull(lvl, "chance"),
                    LegacyYaml.intOrNull(lvl, "cooldown"),
                    condition,
                    conditionTodos,
                    effects));
        }
        return out;
    }
}
