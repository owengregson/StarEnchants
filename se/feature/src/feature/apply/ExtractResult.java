package feature.apply;

/**
 * The outcome of extracting a crystal off gear (§E). On success {@code poppedEntry} is the removed
 * crystal-list entry ({@code "a"} single, {@code "a+b"} multi) for the caller to mint back whole; else null.
 */
public record ExtractResult(boolean ok, String message, String poppedEntry) {

    public static ExtractResult fail(String message) {
        return new ExtractResult(false, message, null);
    }

    public static ExtractResult ok(String poppedEntry, String message) {
        return new ExtractResult(true, message, poppedEntry);
    }
}
