package feature.apply;

/**
 * The outcome of an {@link ItemEnchanter} apply attempt — mutated-or-not plus a colour-coded message
 * for the command to relay (docs/architecture.md §4.2). A value object so validation is testable
 * without a server.
 */
public record ApplyResult(boolean ok, String message) {

    public static ApplyResult ok(String message) {
        return new ApplyResult(true, message);
    }

    public static ApplyResult fail(String message) {
        return new ApplyResult(false, message);
    }
}
