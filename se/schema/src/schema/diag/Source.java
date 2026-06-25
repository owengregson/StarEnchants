package schema.diag;

import java.util.Objects;

/**
 * A position in authored content: {@code file:line:col}. Line/col are 1-based when
 * known; {@link #UNKNOWN} is position-less (synthesized content) and renders as the file.
 */
public record Source(String file, int line, int col) {

    public static final Source UNKNOWN = new Source("<unknown>", -1, -1);

    public Source {
        Objects.requireNonNull(file, "file");
    }

    public static Source of(String file, int line, int col) {
        return new Source(file, line, col);
    }

    public static Source ofFile(String file) {
        return new Source(file, -1, -1);
    }

    public boolean known() {
        return line >= 0;
    }

    /** Same line, shifted to a 1-based column — points at one argument within an effect line. */
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
