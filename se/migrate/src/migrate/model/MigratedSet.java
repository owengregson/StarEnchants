package migrate.model;

import java.util.List;

/**
 * A legacy EliteArmor set translated to the StarEnchants set shape (docs/architecture.md §10);
 * {@code threshold} is the piece count at which the set bonus turns on.
 */
public record MigratedSet(String id, String display, int threshold, List<String> pieces,
                          List<MigratedEffect> effects) {

    public MigratedSet {
        pieces = List.copyOf(pieces);
        effects = List.copyOf(effects);
    }
}
