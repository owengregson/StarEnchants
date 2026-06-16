package com.starenchants.compile;

import com.starenchants.schema.diag.Diagnostics;
import com.starenchants.schema.diag.Source;
import com.starenchants.schema.grammar.EffectLine;
import com.starenchants.schema.spec.Args;
import com.starenchants.schema.spec.ParamSpec;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiles one authored effect/condition line into a validated {@link CompiledLine}:
 * lex → resolve the head to a {@link ParamSpec} → validate arguments into typed
 * {@link Args}.
 *
 * <p>This is the smallest end-to-end slice of the content compiler and the seam
 * between {@code se-schema} (the typed language) and the rest of {@code se-compile}.
 * It never throws — an unknown head and every argument fault are reported as
 * file/line {@link com.starenchants.schema.diag.Diagnostic}s
 * (docs/architecture.md §3.3, §10). An unknown head is warn-and-skipped (the op
 * is dropped, returns empty); a known head with bad arguments still returns the
 * line so the caller can decide, having checked {@link Diagnostics#hasErrors()}.
 */
public final class LineCompiler {

    private final SpecRegistry registry;

    public LineCompiler(SpecRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** Compile an already-lexed line. */
    public Optional<CompiledLine> compile(EffectLine line, Diagnostics diags) {
        Optional<ParamSpec> spec = registry.lookup(line.head());
        if (spec.isEmpty()) {
            diags.error("E_UNKNOWN_KIND", "unknown effect '" + line.head() + "'", line.source(),
                    "run /se docs to list available kinds");
            return Optional.empty();
        }
        Args args = spec.get().parse(line.argTexts(), line.source(), diags);
        return Optional.of(new CompiledLine(spec.get().head(), args, line.source()));
    }

    /** Lex {@code raw} and compile it — the convenience entry point. */
    public Optional<CompiledLine> compile(String raw, Source source, Diagnostics diags) {
        return compile(EffectLine.parse(raw, source), diags);
    }
}
