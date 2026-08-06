package compile.model;

import compile.model.cond.NumExpr;

/**
 * A lowered chance rebate (ADR-0076 part E): the term gate 8 subtracts, plus the feedback the roll it ate may
 * produce. Absent on all but a handful of abilities, so {@link Ability#chanceRebate()} is {@code null} on the
 * hot path and gate 8 pays one null check.
 *
 * <p>Splitting the SAME single roll three ways — {@code roll < effective} activates, {@code roll < base} was
 * eaten by the rebate, otherwise an ordinary miss — is what makes the distribution identical to the
 * subtraction it replaces while giving the blocked band a name.
 *
 * @param points         percentage points subtracted from the base chance; {@code null} = none
 * @param scale          fraction of the base chance ({@code 0..1}) subtracted instead; mutually exclusive
 *                       with {@link #points} (the compiler rejects both)
 * @param message        line shown when the roll lands in the rebated band; {@code null}/blank = none
 * @param messageToActor whether {@link #message} goes to the ACTIVATOR rather than the victim
 * @param soundId        interned sound played with {@link #message}; {@code -1} = none
 * @param spendsCooldown whether the rebated arm KEEPS gate 6's reservation, so the blocked attempt burns the
 *                       window rather than releasing it
 */
public record ChanceRebate(NumExpr points, NumExpr scale, String message, boolean messageToActor, int soundId,
                           boolean spendsCooldown) {

    /** Whether anything is left to feed back once the verdict is recorded — the emit's own null check. */
    public boolean hasFeedback() {
        return (message != null && !message.isEmpty()) || soundId >= 0;
    }
}
