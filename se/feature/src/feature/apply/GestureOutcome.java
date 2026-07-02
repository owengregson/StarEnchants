package feature.apply;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * ADR-0041 — the ONE outcome every apply-family service returns for its gesture listener to commit.
 * {@code commit} → write {@code newTarget} back to the clicked slot ({@code null} OR amount &le; 0 clears it: a
 * destroyed target); {@code consumeCursor} → the service already decremented the cursor stack, write it back
 * ({@code null} at zero); {@code produced} → an extra item handed to the player (give-with-overflow);
 * {@code cue} → sound/particle feedback; {@code message} → preformatted (Messages.format upstream), sent via
 * {@link platform.lang.Messages#sendText}; {@code followUp} runs LAST.
 */
public record GestureOutcome(boolean commit, boolean consumeCursor, ItemStack newTarget,
                             ItemStack produced, Cue cue, String message, Runnable followUp) {

    /** Sound + particle feedback for a committed gesture (both optional). */
    public record Cue(String sound, List<String> particles) {
        public Cue {
            particles = particles == null ? List.of() : List.copyOf(particles);
        }

        /** null when {@code sound} is null — a cue-less commit stays cue-less. */
        public static Cue sound(String sound) {
            return sound == null ? null : new Cue(sound, List.of());
        }

        /** null when there is nothing to play (blank sound + no particles). */
        public static Cue of(String sound, List<String> particles) {
            boolean noSound = sound == null || sound.isBlank();
            if (noSound && (particles == null || particles.isEmpty())) {
                return null;
            }
            return new Cue(sound, particles);
        }
    }

    public static GestureOutcome noop(String message) {
        return new GestureOutcome(false, false, null, null, null, message, null);
    }

    /** Cursor spent but the gear untouched (slot fail / nametag begin). */
    public static GestureOutcome consumedOnly(String message) {
        return new GestureOutcome(false, true, null, null, null, message, null);
    }

    public static GestureOutcome committed(ItemStack newTarget, String message) {
        return new GestureOutcome(true, true, newTarget, null, null, message, null);
    }

    public static GestureOutcome committed(ItemStack newTarget, ItemStack produced, String message) {
        return new GestureOutcome(true, true, newTarget, produced, null, message, null);
    }

    public static GestureOutcome committed(ItemStack newTarget, Cue cue, String message) {
        return new GestureOutcome(true, true, newTarget, null, cue, message, null);
    }

    public static GestureOutcome committed(ItemStack newTarget, ItemStack produced, Cue cue, String message) {
        return new GestureOutcome(true, true, newTarget, produced, cue, message, null);
    }

    /** This outcome with a {@code followUp} to run LAST (after setCursor → updateInventory → cue → message). */
    public GestureOutcome andThen(Runnable followUp) {
        return new GestureOutcome(commit, consumeCursor, newTarget, produced, cue, message, followUp);
    }
}
