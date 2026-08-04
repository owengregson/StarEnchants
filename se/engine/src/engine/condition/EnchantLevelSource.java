package engine.condition;

import java.util.UUID;

/**
 * Where {@link EnchantLevels} gets its numbers: one entity's worn level of one custom enchant, by UUID.
 * Bukkit-free and UUID-keyed on purpose — {@code se-engine} has no dependency on {@code se-item}, and a
 * lookup that never touches a live entity is Folia-safe on either side (the {@code %victim.var.*%} rule).
 *
 * <p>The composition root installs an implementation that reads the pre-flattened {@code WornState}; the
 * lookup must never scan gear, since this is reached from the hit path.
 */
@FunctionalInterface
public interface EnchantLevelSource {

    /** Nothing installed: every read is 0. */
    EnchantLevelSource NONE = (entity, key) -> 0;

    /** {@code entity}'s highest worn level of the enchant {@code key} (lower-cased stem); {@code 0} if absent. */
    int levelOf(UUID entity, String key);
}
