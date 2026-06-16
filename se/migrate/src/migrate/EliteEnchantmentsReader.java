package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** Parse the {@code enchantments.yml} content into the intermediate model. */
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
            out.add(new MigratedEnchant(
                    id,
                    LegacyYaml.string(e, "name", id),
                    String.join(" ", LegacyYaml.stringList(e, "description")),
                    Mappings.trigger(legacyType),
                    Mappings.appliesTo(legacyApplies),
                    LegacyYaml.string(e, "group", "imported").toLowerCase(java.util.Locale.ROOT),
                    levels(LegacyYaml.map(e, "levels")),
                    legacyType,
                    legacyApplies));
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
                effects.add(Mappings.effect(token));
            }
            out.add(new MigratedLevel(
                    level,
                    LegacyYaml.doubleOrNull(lvl, "chance"),
                    LegacyYaml.intOrNull(lvl, "cooldown"),
                    blankToNull(LegacyYaml.string(lvl, "condition", null)),
                    effects));
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
