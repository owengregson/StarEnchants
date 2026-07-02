package feature.apply;

/**
 * The outcome of extracting a crystal off gear (§E). On success {@code poppedEntry} is the removed
 * crystal-list entry ({@code "a"} single, {@code "a+b"} multi) for the caller to mint back whole; else null.
 * {@code message} carries only the FAIL wording — the success message was never surfaced (ADR-0041).
 */
public record ExtractResult(boolean ok, String message, String poppedEntry) {

    public static ExtractResult fail(String message) {
        return new ExtractResult(false, message, null);
    }

    public static ExtractResult ok(String poppedEntry) {
        return new ExtractResult(true, null, poppedEntry);
    }
}
