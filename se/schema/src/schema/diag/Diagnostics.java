package schema.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mutable {@link Diagnostic} collector threaded through one compile so validation
 * never throws (docs/architecture.md §10). Not synchronized: single-thread-confined.
 */
public final class Diagnostics {

    private final List<Diagnostic> entries = new ArrayList<>();

    public Diagnostics add(Diagnostic d) {
        entries.add(Objects.requireNonNull(d, "diagnostic"));
        return this;
    }

    public Diagnostics error(String code, String message, Source source) {
        return add(Diagnostic.error(code, message, source));
    }

    public Diagnostics error(String code, String message, Source source, String fixHint) {
        return add(Diagnostic.error(code, message, source, fixHint));
    }

    public Diagnostics warning(String code, String message, Source source) {
        return add(Diagnostic.warning(code, message, source));
    }

    public Diagnostics warning(String code, String message, Source source, String fixHint) {
        return add(Diagnostic.warning(code, message, source, fixHint));
    }

    public Diagnostics info(String code, String message, Source source) {
        return add(Diagnostic.info(code, message, source));
    }

    // DiagCode overloads — producers reference the constant; the wire string stays code.name().
    public Diagnostics error(DiagCode code, String message, Source source) {
        return add(Diagnostic.error(code, message, source));
    }

    public Diagnostics error(DiagCode code, String message, Source source, String fixHint) {
        return add(Diagnostic.error(code, message, source, fixHint));
    }

    public Diagnostics warning(DiagCode code, String message, Source source) {
        return add(Diagnostic.warning(code, message, source));
    }

    public Diagnostics warning(DiagCode code, String message, Source source, String fixHint) {
        return add(Diagnostic.warning(code, message, source, fixHint));
    }

    public Diagnostics info(DiagCode code, String message, Source source) {
        return add(Diagnostic.info(code, message, source));
    }

    public Diagnostics merge(Diagnostics other) {
        entries.addAll(other.entries);
        return this;
    }

    public boolean hasErrors() {
        for (Diagnostic d : entries) {
            if (d.blocking()) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public long count(Severity severity) {
        return entries.stream().filter(d -> d.severity() == severity).count();
    }

    /** Immutable snapshot in insertion order. */
    public List<Diagnostic> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
