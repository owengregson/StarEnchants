package com.starenchants.schema.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A mutable collector for {@link Diagnostic}s, threaded through a compile.
 *
 * <p>Validation never throws — every stage reports into a {@code Diagnostics} and
 * keeps going, so one bad argument yields one precise finding rather than
 * aborting the whole load (docs/architecture.md §7, §10). After a stage the
 * caller checks {@link #hasErrors()} to decide whether to publish.
 *
 * <p>This type is single-thread-confined for the duration of one compile; it is
 * not synchronized.
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

    /** Append every diagnostic from {@code other} into this collector. */
    public Diagnostics merge(Diagnostics other) {
        entries.addAll(other.entries);
        return this;
    }

    /** @return {@code true} if any collected diagnostic is blocking (an error). */
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

    /** Count of diagnostics of a given severity. */
    public long count(Severity severity) {
        return entries.stream().filter(d -> d.severity() == severity).count();
    }

    /** An immutable snapshot of all collected diagnostics, in insertion order. */
    public List<Diagnostic> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
