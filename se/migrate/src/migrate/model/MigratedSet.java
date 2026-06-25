package migrate.model;

import java.util.List;

/** A migrated EliteArmor set; {@code threshold} is the piece count at which the bonus turns on. */
public record MigratedSet(String id, String display, int threshold, List<String> pieces,
                          List<MigratedEffect> effects) {

    public MigratedSet {
        pieces = List.copyOf(pieces);
        effects = List.copyOf(effects);
    }
}
