package com.starenchants.schema.diag;

/**
 * Severity of a {@link Diagnostic}.
 *
 * <p>{@link #ERROR} is <em>blocking</em>: a compile that produces any error must
 * not publish a Snapshot — the previous one stays live (docs/architecture.md
 * §10, "transactional, all-or-nothing reload"). Warnings and info never block;
 * they are surfaced to the operator but the content still ships.
 */
public enum Severity {
    /** A fault that must prevent the content from going live. */
    ERROR,
    /** A suspicious-but-tolerable condition; content still ships. */
    WARNING,
    /** Purely informational. */
    INFO;

    /** @return {@code true} if a diagnostic of this severity blocks publication. */
    public boolean blocking() {
        return this == ERROR;
    }
}
