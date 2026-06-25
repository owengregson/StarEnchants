package engine.pipeline;

/**
 * The result of running an ability through the activation pipeline (docs/architecture.md §3.3): either
 * it activated, or it stopped at a specific gate. Naming the stop gate lets a runtime trace /
 * {@code /se problems} explain why an ability fired or not, and lets tests assert the exact gate.
 * Constants are ordered to mirror the gate sequence.
 */
public enum GateOutcome {

    /** Blocked: the activator's world is on the ability's blacklist (gate 1). */
    BLOCKED_WORLD,

    /** Blocked: a protection/region provider denied the action (gate 2). */
    BLOCKED_PROTECTION,

    /** Skipped: the ability does not fire on this trigger, or its slot does not apply (gate 3). */
    WRONG_TRIGGER,

    /** Skipped: the ability's level is out of bounds (gate 4). */
    OUT_OF_LEVEL,

    /** Skipped: the ability is suppressed by a {@code DISABLE_*} this activation (gate 5). */
    SUPPRESSED,

    /** Skipped: a cooldown scope is still active (gate 6). */
    ON_COOLDOWN,

    /** Skipped: the condition evaluated to STOP (gate 7). */
    CONDITION_FAILED,

    /** Skipped: the chance roll did not pass (gate 8). */
    CHANCE_FAILED,

    /** Cancelled: a {@code PreActivate} listener cancelled the activation (gate 9). */
    CANCELLED,

    /** Skipped: an active soul gem could not pay the soul cost (gate 10). */
    NO_SOULS,

    /** Activated: every gate passed; souls were debited and cooldowns armed (gates 10–11). */
    ACTIVATED;

    public boolean activated() {
        return this == ACTIVATED;
    }
}
