package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import migrate.model.MigratedEffect;
import migrate.model.MigratedSet;

/**
 * Reads one EliteArmor armour-set file ({@code armor/<set>.yml}) into a {@link MigratedSet}
 * (docs/architecture.md §10). EliteArmor sets are full four-piece sets whose {@code Effects} are
 * passive bonuses; the migrated StarEnchants set is DEFENSE-triggered, so a {@code REDUCTION} maps to
 * {@code REDUCE_DAMAGE} while attack-direction or named bonuses are flagged for manual porting via
 * {@link Mappings#setEffect}. The {@code Required-Items} count becomes the {@code pieces} threshold.
 * Parses with SnakeYAML directly via {@link LegacyYaml}.
 */
public final class EliteArmorReader {

    private EliteArmorReader() {
    }

    /**
     * Parse one set file. {@code id} is the output set id (typically the source file name without
     * extension, e.g. {@code ancient}); the display defaults to its capitalisation.
     */
    public static MigratedSet read(String id, String yaml) {
        Map<?, ?> root = LegacyYaml.parse(yaml);
        // A set needs a positive piece threshold (the SE loader rejects pieces < 1); clamp a missing/zero
        // Required-Items up to the full four-piece set rather than emit an unloadable pieces: 0.
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
