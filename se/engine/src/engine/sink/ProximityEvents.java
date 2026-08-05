package engine.sink;

import org.bukkit.entity.LivingEntity;

/**
 * The sink's view of a NEARBY-EVENT announcement — a seam for the same reason {@link SummonPayloads} is one:
 * firing a trigger is the feature layer's business and no engine class may reach it. {@code PROXIMITY_ANNOUNCE}
 * calls it; the feature layer walks the observers and fires {@code PROXIMITY_EVENT} on each. Wired at the
 * composition root.
 */
@FunctionalInterface
public interface ProximityEvents {

    /** The inert default for non-root construction sites (tests, tester suites): nothing is ever announced. */
    ProximityEvents NONE = (subject, tag, radius) -> { };

    /**
     * Fire {@code PROXIMITY_EVENT} on every player within {@code radius} of {@code subject} — never the
     * subject themselves — with {@code tag} readable as {@code %proximityevent%}, so an observer of one
     * nearby event cannot proc on another's.
     */
    void announce(LivingEntity subject, String tag, double radius);
}
