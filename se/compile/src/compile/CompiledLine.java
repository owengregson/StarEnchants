package compile;

import schema.diag.Source;
import schema.spec.Args;

/** One compiled effect/condition line: canonical head, typed {@link Args}, originating {@link Source} — no strings left to parse at runtime (docs/architecture.md §3.2, §4.1). */
public record CompiledLine(String head, Args args, Source source) {
}
