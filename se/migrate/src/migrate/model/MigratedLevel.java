package migrate.model;

import java.util.List;

/**
 * One enchant level after migration: the per-level chance/cooldown/condition plus the translated
 * effects (docs/architecture.md §10). {@code chance} / {@code cooldown} / {@code condition} are
 * {@code null} when the legacy config omitted them (the writer then omits them too, letting the
 * StarEnchants defaults apply).
 */
public record MigratedLevel(int level, Double chance, Integer cooldown, String condition,
                            List<MigratedEffect> effects) {

    public MigratedLevel {
        effects = List.copyOf(effects);
    }
}
