package compile;

import schema.diag.Source;
import schema.spec.Args;

/**
 * The validated result of compiling one authored effect/condition line: canonical head,
 * typed {@link Args}, and originating {@link Source}. The boundary between authored text and
 * the compiled world — carries no strings to parse at runtime (docs/architecture.md §3.2, §4.1).
 */
public record CompiledLine(String head, Args args, Source source) {
}
