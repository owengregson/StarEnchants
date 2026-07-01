package feature.apply;

/**
 * The outcome of an {@link ItemEnchanter} apply attempt — ok-or-not plus a message for the command to relay.
 * The optional {@link Reason} lets a caller branch on WHY an attempt failed structurally, instead of sniffing
 * the rendered message text (which breaks the moment that message is customised in lang.yml).
 */
public record ApplyResult(boolean ok, String message, Reason reason) {

    /** Why an attempt was rejected, for callers that swap in their own wording (e.g. the crystal-slot case). */
    public enum Reason {
        /** No structural reason — the message stands on its own. */
        NONE,
        /** The target has no free crystal slot (a {@code CrystalService} substitutes its own {@code crystal.no-slots}). */
        NO_CRYSTAL_SLOTS
    }

    public static ApplyResult ok(String message) {
        return new ApplyResult(true, message, Reason.NONE);
    }

    public static ApplyResult fail(String message) {
        return new ApplyResult(false, message, Reason.NONE);
    }

    /** A failure carrying a structural {@link Reason} a caller may branch on. */
    public static ApplyResult fail(String message, Reason reason) {
        return new ApplyResult(false, message, reason);
    }
}
