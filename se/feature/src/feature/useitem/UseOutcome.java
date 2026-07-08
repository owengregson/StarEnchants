package feature.useitem;

/**
 * The result of right-clicking a use-item (§3.6, docs/decisions/0048-use-items.md), collapsed from the engine's
 * {@link engine.run.UseAttempt} by the fixed precedence (activated &rarr; on-cooldown &rarr; condition-failed
 * &rarr; chance-failed &rarr; blocked). The {@link feature.useitem.UseItemListener} maps each to the universal
 * lang feedback and decides whether to consume the held item.
 *
 * @param result                 which branch the right-click landed in
 * @param cooldownRemainingTicks remaining cooldown ticks (only meaningful for {@link Result#ON_COOLDOWN})
 * @param conditionSource        the authored condition string that failed ({@link Result#CONDITION_FAILED};
 *                               may be {@code ""}), rendered as {@code {CONDITION}} in the fail message
 */
public record UseOutcome(Result result, long cooldownRemainingTicks, String conditionSource) {

    /** The activation outcome, ordered by the §3.6 precedence the service collapses candidates through. */
    public enum Result {
        /** A candidate passed every gate — its effects ran (consume the item if the def is consumable). */
        ACTIVATED,
        /** A candidate is still on cooldown — send the cooldown feedback with the remaining time. */
        ON_COOLDOWN,
        /** A candidate's condition failed — send the fail feedback with the authored condition. */
        CONDITION_FAILED,
        /** A candidate's chance roll did not land — silent (nothing consumed). */
        CHANCE_FAILED,
        /** No candidate reached an activation-relevant gate (world/suppression/souls/…) — silent. */
        BLOCKED
    }

    public static UseOutcome activated() {
        return new UseOutcome(Result.ACTIVATED, 0L, "");
    }

    public static UseOutcome onCooldown(long remainingTicks) {
        return new UseOutcome(Result.ON_COOLDOWN, remainingTicks, "");
    }

    public static UseOutcome conditionFailed(String conditionSource) {
        return new UseOutcome(Result.CONDITION_FAILED, 0L, conditionSource == null ? "" : conditionSource);
    }

    public static UseOutcome chanceFailed() {
        return new UseOutcome(Result.CHANCE_FAILED, 0L, "");
    }

    public static UseOutcome blocked() {
        return new UseOutcome(Result.BLOCKED, 0L, "");
    }
}
