package migrate.model;

import java.util.List;

/**
 * A legacy enchant translated to the StarEnchants shape (docs/architecture.md §10): a stable
 * {@code id} (the output file name), display/description, the mapped {@code trigger} and
 * {@code appliesTo}, the {@code group}, and the per-level data. {@code trigger} is {@code null} when
 * the legacy type had no StarEnchants equivalent (the writer flags it with a {@code # TODO}); the
 * {@code legacyTrigger}/{@code legacyApplies} originals are retained for the review comments.
 */
public record MigratedEnchant(String id, String display, String description, String trigger,
                              List<String> appliesTo, String group, List<MigratedLevel> levels,
                              String legacyTrigger, String legacyApplies) {

    public MigratedEnchant {
        appliesTo = List.copyOf(appliesTo);
        levels = List.copyOf(levels);
    }
}
