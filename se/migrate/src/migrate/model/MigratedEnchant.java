package migrate.model;

import java.util.List;

/**
 * A legacy enchant translated to the StarEnchants shape. {@code trigger} is {@code null} when the legacy
 * type had no SE equivalent; the {@code legacy*} originals feed review comments. {@code repeatTicks} is the
 * REPEATING period in ticks (from EE's {@code REPEATING-<seconds>}), 0 for a non-repeating enchant.
 */
public record MigratedEnchant(String id, String display, String description, String trigger,
                              List<String> appliesTo, String group, List<MigratedLevel> levels,
                              String legacyTrigger, String legacyApplies, int repeatTicks) {

    public MigratedEnchant {
        appliesTo = List.copyOf(appliesTo);
        levels = List.copyOf(levels);
        repeatTicks = Math.max(0, repeatTicks);
    }

    /** A non-repeating enchant (the AE/EA path, and any EE enchant that is not {@code REPEATING}). */
    public MigratedEnchant(String id, String display, String description, String trigger,
                           List<String> appliesTo, String group, List<MigratedLevel> levels,
                           String legacyTrigger, String legacyApplies) {
        this(id, display, description, trigger, appliesTo, group, levels, legacyTrigger, legacyApplies, 0);
    }
}
