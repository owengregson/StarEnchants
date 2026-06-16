package schema.diag;

import java.util.Objects;

/**
 * A position in authored content: {@code file:line:col}.
 *
 * <p>Sources are carried from the SnakeYAML loader through every compile stage so
 * a fault is always reported where the operator wrote it (docs/architecture.md
 * §10). They are cheap immutable value objects; the compiler threads them, never
 * recomputes them.
 *
 * <p>Line and column are 1-based when known. {@link #UNKNOWN} represents a
 * position-less origin (synthesized content, defaults) and renders as just the
 * file label.
 */
public record Source(String file, int line, int col) {

    /** A position-less source for synthesized or origin-less content. */
    public static final Source UNKNOWN = new Source("<unknown>", -1, -1);

    public Source {
        Objects.requireNonNull(file, "file");
    }

    public static Source of(String file, int line, int col) {
        return new Source(file, line, col);
    }

    /** A whole-file source with no specific position. */
    public static Source ofFile(String file) {
        return new Source(file, -1, -1);
    }

    /** @return {@code true} if this source carries a concrete line/column. */
    public boolean known() {
        return line >= 0;
    }

    /**
     * A copy of this source shifted to a specific column on the same line — used
     * to point at an individual argument within an effect line.
     *
     * @param newCol the 1-based column to point at
     */
    public Source atColumn(int newCol) {
        return new Source(file, line, newCol);
    }

    @Override
    public String toString() {
        if (!known()) {
            return file;
        }
        return col >= 0 ? file + ":" + line + ":" + col : file + ":" + line;
    }
}
