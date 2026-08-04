package engine.condition;

import java.util.UUID;

/**
 * Where the worn-gear facts get their numbers: one entity's pre-flattened {@code WornState}, by UUID.
 * Bukkit-free and UUID-keyed on purpose — {@code se-engine} has no dependency on {@code se-item}, and a
 * lookup that never touches a live entity is Folia-safe on either side (the {@code %victim.var.*%} rule).
 *
 * <p>The composition root installs the implementation; every lookup here must be a read off the flattened
 * state and never a gear scan, since this is reached from the hit path.
 */
public interface WornFactSource {

    /** Nothing installed: every read is 0. */
    WornFactSource NONE = new WornFactSource() {
        @Override
        public int levelOf(UUID entity, String key) {
            return 0;
        }

        @Override
        public int heroicPieces(UUID entity) {
            return 0;
        }
    };

    /** {@code entity}'s highest worn level of the enchant {@code key} (lower-cased stem); {@code 0} if absent. */
    int levelOf(UUID entity, String key);

    /** How many of {@code entity}'s worn armour pieces carry a heroic upgrade (0..4). */
    int heroicPieces(UUID entity);
}
