package feature.apply;

/**
 * The outcome of extracting a crystal back off gear (docs/v3-directives.md §E) — the inverse of
 * {@link ItemEnchanter#applyCrystalEntry}. On success {@code poppedEntry} is the removed crystal-list
 * entry ({@code "a"} single, {@code "a+b"} multi), which the caller mints back into a whole physical
 * crystal item; {@code null} otherwise.
 */
public record ExtractResult(boolean ok, String message, String poppedEntry) {

    public static ExtractResult fail(String message) {
        return new ExtractResult(false, message, null);
    }

    public static ExtractResult ok(String poppedEntry, String message) {
        return new ExtractResult(true, message, poppedEntry);
    }
}
