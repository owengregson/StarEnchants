package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import migrate.model.MigratedEffect;
import migrate.model.MigratedSet;

/**
 * Reads one EliteArmor armour-set file into a {@link MigratedSet} (docs/architecture.md §10). The
 * migrated SE set is DEFENSE-triggered, so a {@code REDUCTION} maps cleanly while attack-direction or
 * named bonuses are flagged for manual porting (see {@link Mappings#setEffect}); {@code Required-Items}
 * becomes the piece threshold.
 */
public final class EliteArmorReader {

    private EliteArmorReader() {
    }

    /** {@code id} is the output set id (typically the source file name, e.g. {@code ancient}). */
    public static MigratedSet read(String id, String yaml) {
        Map<?, ?> root = LegacyYaml.parse(yaml);
        // The SE loader rejects pieces < 1; clamp a missing/zero Required-Items up to the full four-piece set.
        int threshold = Math.max(1, LegacyYaml.intOr(root, "Required-Items", 4));
        List<MigratedEffect> effects = new ArrayList<>();
        for (String token : LegacyYaml.stringList(root, "Effects")) {
            effects.add(Mappings.setEffect(token));
        }
        return new MigratedSet(id, capitalize(id), threshold,
                List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"), effects);
    }

    private static String capitalize(String id) {
        if (id == null || id.isEmpty()) {
            return "Imported Set";
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1).toLowerCase(Locale.ROOT);
    }
}
