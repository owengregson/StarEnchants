package schema.diag;

/**
 * Severity of a {@link Diagnostic}. Any {@link #ERROR} blocks the all-or-nothing
 * reload — no Snapshot publishes (docs/architecture.md §10); WARNING/INFO ship.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO;

    public boolean blocking() {
        return this == ERROR;
    }
}
