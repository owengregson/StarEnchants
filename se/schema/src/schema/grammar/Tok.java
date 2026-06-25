package schema.grammar;

/** A lexed token: raw {@code text} and the 1-based {@code col} for argument-precise diagnostics. */
public record Tok(String text, int col) {

    public String trimmed() {
        return text.trim();
    }
}
