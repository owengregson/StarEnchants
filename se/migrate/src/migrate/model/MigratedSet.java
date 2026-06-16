package migrate.model;

import java.util.List;

/**
 * A legacy armour set (EliteArmor) translated to the StarEnchants set shape (docs/architecture.md §10):
 * a stable {@code id} (output file name), display, the piece-count {@code threshold} at which the set
 * bonus turns on, the pieces it spans, and the translated set-bonus {@code effects}.
 */
public record MigratedSet(String id, String display, int threshold, List<String> pieces,
                          List<MigratedEffect> effects) {

    public MigratedSet {
        pieces = List.copyOf(pieces);
        effects = List.copyOf(effects);
    }
}
