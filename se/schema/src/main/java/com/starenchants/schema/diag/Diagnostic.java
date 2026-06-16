package com.starenchants.schema.diag;

import java.util.Objects;

/**
 * One compile- or load-time finding: a {@link Severity}, a stable machine code,
 * a human message, the {@link Source} it occurred at, and an optional fix hint.
 *
 * <p>Diagnostics are the typed-language alternative to the originals' fail-at-fire
 * {@code NumberFormatException}: a malformed line becomes a precise, file/line
 * addressable report at load, never a mid-combat crash (docs/architecture.md
 * §1.2 D5, §7, §10). The {@code code} (e.g. {@code E_RANGE}, {@code W_EXTRA_ARGS})
 * is stable so tooling and {@code /se problems} can group and reference findings.
 */
public record Diagnostic(Severity severity, String code, String message, Source source, String fixHint) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        // fixHint is nullable.
    }

    public static Diagnostic error(String code, String message, Source source) {
        return new Diagnostic(Severity.ERROR, code, message, source, null);
    }

    public static Diagnostic error(String code, String message, Source source, String fixHint) {
        return new Diagnostic(Severity.ERROR, code, message, source, fixHint);
    }

    public static Diagnostic warning(String code, String message, Source source) {
        return new Diagnostic(Severity.WARNING, code, message, source, null);
    }

    public static Diagnostic warning(String code, String message, Source source, String fixHint) {
        return new Diagnostic(Severity.WARNING, code, message, source, fixHint);
    }

    public static Diagnostic info(String code, String message, Source source) {
        return new Diagnostic(Severity.INFO, code, message, source, null);
    }

    /** @return {@code true} if this diagnostic blocks publication (i.e. is an error). */
    public boolean blocking() {
        return severity.blocking();
    }

    /**
     * A single-line, operator-facing rendering:
     * {@code file:line:col: error[E_RANGE]: message (hint: …)}.
     */
    public String render() {
        StringBuilder sb = new StringBuilder()
                .append(source)
                .append(": ")
                .append(severity.name().toLowerCase())
                .append('[')
                .append(code)
                .append("]: ")
                .append(message);
        if (fixHint != null && !fixHint.isBlank()) {
            sb.append(" (hint: ").append(fixHint).append(')');
        }
        return sb.toString();
    }
}
