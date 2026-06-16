package engine.trigger;

import java.util.Objects;

/**
 * The plain-data {@link TriggerKind} the builtin triggers use (docs/architecture.md
 * §3.7). A trigger is declarative metadata — name, combat direction, and routing flags —
 * so a record is its natural shape; add-ons may implement {@link TriggerKind} directly
 * for anything richer.
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

    /** An attacker-side combat trigger that scans equipment and needs a target. */
    public static Trigger attack(String name) {
        return new Trigger(name, Direction.ATTACK, false, true, true);
    }

    /** A defender-side combat trigger that scans equipment and needs a target. */
    public static Trigger defense(String name) {
        return new Trigger(name, Direction.DEFENSE, false, true, true);
    }

    /** A non-combat trigger that scans equipment and has no target (PASSIVE, FALL-less, …). */
    public static Trigger neutral(String name) {
        return new Trigger(name, Direction.NEUTRAL, false, true, false);
    }

    /** A non-combat trigger read from the held item only (HELD, BREAK, ITEM_DAMAGE). */
    public static Trigger held(String name) {
        return new Trigger(name, Direction.NEUTRAL, true, false, false);
    }
}
