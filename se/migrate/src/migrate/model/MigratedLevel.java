package migrate.model;

import java.util.List;

/**
 * One enchant level after migration (docs/architecture.md §10). {@code chance}/{@code cooldown}/
 * {@code condition} are {@code null} when the legacy config omitted them (the writer omits them too, so SE
 * defaults apply). {@code condition} is the combined gate from the AE condition lines that mapped;
 * {@code conditionTodos} carries the lines that could NOT map (a {@code # TODO} each), so none is silently
 * dropped.
 */
public record MigratedLevel(int level, Double chance, Integer cooldown, String condition,
                            List<String> conditionTodos, List<MigratedEffect> effects) {

    public MigratedLevel {
        conditionTodos = List.copyOf(conditionTodos);
        effects = List.copyOf(effects);
    }

    /** A level with no condition TODOs (the EliteEnchantments/EliteArmor path — v1 conditions map 1:1). */
    public MigratedLevel(int level, Double chance, Integer cooldown, String condition,
                         List<MigratedEffect> effects) {
        this(level, chance, cooldown, condition, List.of(), effects);
    }
}
