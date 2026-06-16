package com.starenchants.schema.grammar;

/**
 * A lexed token: its raw {@code text} and the 1-based {@code col} of its first
 * character on the source line. The column lets a later stage attach an
 * argument-precise {@link com.starenchants.schema.diag.Source} to a diagnostic.
 */
public record Tok(String text, int col) {

    /** The token text with surrounding whitespace removed (for value reads). */
    public String trimmed() {
        return text.trim();
    }
}
