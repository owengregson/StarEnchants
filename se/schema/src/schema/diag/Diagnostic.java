package schema.diag;

import java.util.Objects;

/**
 * One compile- or load-time finding (docs/architecture.md §10). The {@code code}
 * (e.g. {@code E_RANGE}) is stable so tooling can group and reference findings.
 *
 * <p>Producers mint findings through the {@link DiagCode} factories; the canonical constructor stays public
 * for test fixtures that need an off-catalogue code (ADR-0042).
 */
public record Diagnostic(Severity severity, String code, String message, Source source, String fixHint) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
    }

    // DiagCode factories — the single-source form producers use; the wire string is code.name().
    public static Diagnostic error(DiagCode code, String message, Source source) {
        return new Diagnostic(Severity.ERROR, code.name(), message, source, null);
    }

    public static Diagnostic error(DiagCode code, String message, Source source, String fixHint) {
        return new Diagnostic(Severity.ERROR, code.name(), message, source, fixHint);
    }

    public static Diagnostic warning(DiagCode code, String message, Source source) {
        return new Diagnostic(Severity.WARNING, code.name(), message, source, null);
    }

    public static Diagnostic warning(DiagCode code, String message, Source source, String fixHint) {
        return new Diagnostic(Severity.WARNING, code.name(), message, source, fixHint);
    }

    public static Diagnostic info(DiagCode code, String message, Source source) {
        return new Diagnostic(Severity.INFO, code.name(), message, source, null);
    }

    public boolean blocking() {
        return severity.blocking();
    }

    /** True if this finding carries {@code code} — the contract assertion, vs. brittle message matching. */
    public boolean is(DiagCode code) {
        return code != null && code.name().equals(this.code);
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
