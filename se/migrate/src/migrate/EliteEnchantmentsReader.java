package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import migrate.model.MigratedCondition;
import migrate.model.MigratedEffect;
import migrate.model.MigratedEnchant;
import migrate.model.MigratedLevel;

/**
 * Reads an EliteEnchantments {@code enchantments.yml} (the {@code Enchants:} map of per-enchant
 * definitions) into {@link MigratedEnchant}s, mapping triggers/applies/effects through {@link Mappings}
 * (docs/architecture.md §10). Parses with SnakeYAML directly via {@link LegacyYaml}.
 */
public final class EliteEnchantmentsReader {

    private EliteEnchantmentsReader() {
    }

    public static List<MigratedEnchant> read(String yaml) {
        Map<?, ?> enchants = LegacyYaml.map(LegacyYaml.parse(yaml), "Enchants");
        if (enchants == null) {
            return List.of();
        }
        List<MigratedEnchant> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : enchants.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> e)) {
                continue;
            }
            String id = String.valueOf(entry.getKey());
            String legacyType = LegacyYaml.string(e, "type", null);
            String legacyApplies = LegacyYaml.string(e, "applies", null);
            // DEFENSE enchants flip the foe selector to @Attacker (the entity that hit the wielder).
            boolean defenseDir = "DEFENSE".equals(Mappings.trigger(legacyType));
            out.add(new MigratedEnchant(
                    id,
                    LegacyYaml.string(e, "name", id),
                    String.join(" ", LegacyYaml.stringList(e, "description")),
                    Mappings.trigger(legacyType),
                    Mappings.appliesTo(legacyApplies),
                    LegacyYaml.string(e, "group", "imported").toLowerCase(java.util.Locale.ROOT),
                    levels(LegacyYaml.map(e, "levels"), defenseDir),
                    legacyType,
                    legacyApplies,
                    Mappings.repeatTicks(legacyType)));
        }
        return out;
    }

    private static List<MigratedLevel> levels(Map<?, ?> levels, boolean defenseDir) {
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
                // One EE token can expand to several SE effects (a WRATH/FROST/ROT_DECAY compound).
                effects.addAll(Mappings.effects(token, defenseDir));
            }
            // An unmappable condition is TODO'd, never emitted raw — that would be invalid SE grammar.
            String legacyCondition = blankToNull(LegacyYaml.string(lvl, "condition", null));
            String mappedCondition = null;
            List<String> conditionTodos = new ArrayList<>();
            if (legacyCondition != null) {
                MigratedCondition cond = Mappings.eeCondition(legacyCondition);
                if (cond.mapped()) {
                    mappedCondition = cond.expr();
                } else {
                    conditionTodos.add(legacyCondition + " — " + cond.todo());
                }
            }
            out.add(new MigratedLevel(
                    level,
                    LegacyYaml.doubleOrNull(lvl, "chance"),
                    LegacyYaml.intOrNull(lvl, "cooldown"),
                    mappedCondition,
                    conditionTodos,
                    effects));
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
