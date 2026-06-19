package feature.carrier;

import java.util.List;

/**
 * The outcome of attempting to apply a carrier (book/scroll/dust) to a target item — whether the carrier
 * was consumed, a player-facing message, and optional §I apply-feedback ({@code sound} + {@code particles})
 * for the interaction layer to play on the player's own thread. {@code consumed} tells the interaction
 * layer whether the carrier was actually used (so it should commit the inventory changes) versus a no-op
 * (bad target, missing def) that leaves both stacks untouched.
 *
 * @param consumed  whether a carrier use was spent (the target and/or carrier were mutated)
 * @param message   the colour-coded message to show the player
 * @param sound     a namespaced sound token to play on success, or {@code null}
 * @param particles particle tokens to spawn at the player on success (may be empty)
 */
public record CarrierResult(boolean consumed, String message, String sound, List<String> particles) {

    public CarrierResult {
        particles = particles == null ? List.of() : List.copyOf(particles);
    }

    /** A carrier use was spent (applied, failed, destroyed, or protected), with no feedback. */
    public static CarrierResult consumed(String message) {
        return new CarrierResult(true, message, null, List.of());
    }

    /** A carrier use was spent, with §I sound/particle feedback to play on the player's own thread. */
    public static CarrierResult consumed(String message, String sound, List<String> particles) {
        return new CarrierResult(true, message, sound, particles);
    }

    /** Nothing happened — the carrier and target are untouched (e.g. an ineligible target). */
    public static CarrierResult noop(String message) {
        return new CarrierResult(false, message, null, List.of());
    }
}
