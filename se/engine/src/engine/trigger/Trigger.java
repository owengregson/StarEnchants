package engine.trigger;

import java.util.Objects;

/**
 * The plain-data {@link TriggerKind} the builtin triggers use (docs/architecture.md §3.7).
 * Add-ons may implement {@link TriggerKind} directly for anything richer.
 */
public record Trigger(
        String name,
        TriggerKind.Direction direction,
        boolean usesHeld,
        boolean scansEquipment,
        boolean needsTarget) implements TriggerKind {

    public Trigger {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(direction, "direction");
    }

    /** Attacker-side combat: scans equipment, needs a target. */
    public static Trigger attack(String name) {
        return new Trigger(name, Direction.ATTACK, false, true, true);
    }

    /** Defender-side combat: scans equipment, needs a target. */
    public static Trigger defense(String name) {
        return new Trigger(name, Direction.DEFENSE, false, true, true);
    }

    /** Non-combat: scans equipment, no target. */
    public static Trigger neutral(String name) {
        return new Trigger(name, Direction.NEUTRAL, false, true, false);
    }

    /** Non-combat: read from the held item only, no target. */
    public static Trigger held(String name) {
        return new Trigger(name, Direction.NEUTRAL, true, false, false);
    }
}
