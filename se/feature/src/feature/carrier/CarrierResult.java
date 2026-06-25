package feature.carrier;

import java.util.List;

/**
 * The outcome of applying a carrier (book/scroll/dust) to a target — plus optional §I apply-feedback
 * ({@code sound} + {@code particles}) for the interaction layer to play on the player's own thread.
 * {@code consumed} tells that layer whether to commit the inventory changes (a use was spent) versus a
 * no-op (bad target, missing def) that leaves both stacks untouched.
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
