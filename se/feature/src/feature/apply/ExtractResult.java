package feature.apply;

/**
 * The outcome of extracting a crystal back off gear (docs/v3-directives.md §E) — the inverse of
 * {@link ItemEnchanter#applyCrystalEntry}. On success {@code poppedEntry} is the removed gear crystal-list
 * entry ({@code "a"} for a single, {@code "a+b"} for a multi), which the caller mints back into a whole
 * physical crystal item ("extraction returns the multi-crystal as a whole"). {@code message} is the chat
 * feedback either way.
 *
 * @param ok          whether a crystal was extracted
 * @param message     chat feedback
 * @param poppedEntry the removed crystal-list entry (only set when {@code ok}), or {@code null}
 */
public record ExtractResult(boolean ok, String message, String poppedEntry) {

    public static ExtractResult fail(String message) {
        return new ExtractResult(false, message, null);
    }

    public static ExtractResult ok(String poppedEntry, String message) {
        return new ExtractResult(true, message, poppedEntry);
    }
}
