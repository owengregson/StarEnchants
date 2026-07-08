package engine.run;

/**
 * The compact result of one COLD-path use-item activation (§3.6, docs/decisions/0048-use-items.md) — a
 * right-click item runs its explicit candidate abilities through the SAME {@link engine.pipeline.ActivationPipeline}
 * gate sequence every other source uses, but the caller needs a summary (not the void hot-path fold) to render
 * the universal use-item feedback. The feature layer collapses this to its own outcome by precedence:
 * activated &rarr; on-cooldown &rarr; condition-failed &rarr; chance-failed &rarr; blocked.
 *
 * @param activated                any candidate passed every gate and ran its effects
 * @param onCooldown               a candidate stopped at the cooldown gate (gate 6)
 * @param cooldownRemainingTicks   the greatest remaining cooldown across the blocked candidates (0 if none)
 * @param conditionCandidateIndex  the index (into the caller's candidate array, aligned to the def's ability
 *                                 order) of the FIRST candidate that stopped at the condition gate, else {@code -1}
 * @param chanceFailed             a candidate stopped at the chance-roll gate (gate 8)
 */
public record UseAttempt(boolean activated, boolean onCooldown, long cooldownRemainingTicks,
                         int conditionCandidateIndex, boolean chanceFailed) {

    /** The all-blocked/no-candidate result — every field inert. */
    public static UseAttempt none() {
        return new UseAttempt(false, false, 0L, -1, false);
    }
}
