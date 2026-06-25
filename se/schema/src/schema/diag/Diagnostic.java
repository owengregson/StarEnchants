package schema.diag;

import java.util.Objects;

/**
 * One compile- or load-time finding (docs/architecture.md §10). The {@code code}
 * (e.g. {@code E_RANGE}) is stable so tooling can group and reference findings.
 */
public record Diagnostic(Severity severity, String code, String message, Source source, String fixHint) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
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
