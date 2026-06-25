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
 * (docs/architecture.md §10). Unlike EliteEnchantments: AE keeps each enchant at the document ROOT (no
 * {@code Enchants:} wrapper), uses its own {@code type}/{@code applies}/effect vocabulary (mapped via
 * {@link Mappings} {@code ae*}), and gives effects a space-separated target. Conditions translate where
 * they map to an SE gate; an unmappable result/variable becomes a {@code # TODO}, never a silently-wrong
 * gate.
 */
public final class AdvancedEnchantmentsReader {

    private AdvancedEnchantmentsReader() {
    }

    public static List<MigratedEnchant> read(String yaml) {
        Map<?, ?> root = LegacyYaml.parse(yaml);
        List<MigratedEnchant> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> e)) {
                continue; // not an enchant mapping
            }
            String id = String.valueOf(entry.getKey());
            String legacyType = LegacyYaml.string(e, "type", null);
            List<String> applies = LegacyYaml.stringList(e, "applies");
            // AE @Victim/@Attacker name opposite entities on DEFENSE vs ATTACK, so effect/selector mapping
            // is direction-aware, derived from the mapped trigger.
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
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> lvl)) {
                continue;
            }
            List<MigratedEffect> effects = new ArrayList<>();
            for (String token : LegacyYaml.stringList(lvl, "effects")) {
                effects.add(Mappings.aeEffect(token, defenseDirection));
            }
            // Plain boolean gates combine with `&&`; a flow/chance clause is top-level-only (a condition
            // admits at most one), so if any clause is present it is emitted alone and the rest become TODOs.
            List<String> mappedGates = new ArrayList<>();
            List<String> mappedClauses = new ArrayList<>();
            List<String> conditionTodos = new ArrayList<>();
            for (String condLine : LegacyYaml.stringList(lvl, "conditions")) {
                MigratedCondition condition = Mappings.aeCondition(condLine);
                if (!condition.mapped()) {
                    conditionTodos.add(condition.todo());
                } else if (condition.clauseForm()) {
                    mappedClauses.add(condition.expr());
                } else {
                    mappedGates.add(condition.expr());
                }
            }
            String condition;
            if (!mappedClauses.isEmpty()) {
                condition = mappedClauses.get(0);
                mappedClauses.stream().skip(1).forEach(e -> conditionTodos.add(
                        "a condition admits one flow/chance clause; this extra clause was dropped: " + e));
                mappedGates.forEach(e -> conditionTodos.add(
                        "a boolean gate cannot combine with a flow/chance clause; port manually: " + e));
            } else {
                condition = mappedGates.isEmpty() ? null
                        : mappedGates.size() == 1 ? mappedGates.get(0)
                        : mappedGates.stream().map(e -> "(" + e + ")").collect(Collectors.joining(" && "));
            }
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
