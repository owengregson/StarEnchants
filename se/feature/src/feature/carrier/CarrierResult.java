package feature.carrier;

/**
 * The outcome of attempting to apply a carrier (book/scroll/dust) to a target item — whether the carrier
 * was consumed and a player-facing message. {@code consumed} tells the interaction layer whether the
 * carrier was actually used (so it should commit the inventory changes) versus a no-op (bad target,
 * missing def) that leaves both stacks untouched.
 *
 * @param consumed whether a carrier use was spent (the target and/or carrier were mutated)
 * @param message  the colour-coded message to show the player
 */
public record CarrierResult(boolean consumed, String message) {

    /** A carrier use was spent (applied, failed, destroyed, or protected). */
    public static CarrierResult consumed(String message) {
        return new CarrierResult(true, message);
    }

    /** Nothing happened — the carrier and target are untouched (e.g. an ineligible target). */
    public static CarrierResult noop(String message) {
        return new CarrierResult(false, message);
    }
}
