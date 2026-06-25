package compile;

import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.Args;
import schema.spec.Param;
import schema.spec.ParamSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiles one authored effect/condition line into a validated {@link CompiledLine}:
 * resolve the head to a {@link ParamSpec}, then validate arguments into typed {@link Args}
 * (docs/architecture.md §3.3, §10). The seam between {@code se-schema} and {@code se-compile}.
 *
 * <p>Never throws. An unknown head is warn-and-skipped (returns empty); a known head with
 * bad arguments still returns the line so the caller can decide after checking
 * {@link Diagnostics#hasErrors()}.
 *
 * <p><strong>Terse vs verbose (ADR-0016).</strong> A verbose line's named values are ordered
 * into the spec's positional order <em>as a list</em> (never re-joined on {@code :}), so a string
 * argument containing {@code :} or {@code @} survives intact, then run through the same
 * {@code ParamSpec.parse} path as a terse line.
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
        Args args = line.isVerbose()
                ? parseVerbose(spec.get(), line, diags)
                : spec.get().parse(line.argTexts(), line.source(), diags);
        return Optional.of(new CompiledLine(spec.get().head(), args, line.source()));
    }

    /** Lex {@code raw} and compile it — the convenience entry point. */
    public Optional<CompiledLine> compile(String raw, Source source, Diagnostics diags) {
        return compile(EffectLine.parse(raw, source), diags);
    }

    /**
     * Validate a verbose (named) line: reject unknown/missing-required params with precise diagnostics,
     * then order the values into positional order and run {@link ParamSpec#parse} for typing/range.
     */
    private static Args parseVerbose(ParamSpec spec, EffectLine line, Diagnostics diags) {
        Map<String, String> named = line.named();
        List<Param> params = spec.params();

        List<String> validNames = new ArrayList<>(params.size());
        for (Param p : params) {
            validNames.add(p.name());
        }
        for (String key : named.keySet()) {
            if (!validNames.contains(key)) {
                diags.error("E_UNKNOWN_EFFECT_PARAM",
                        "unknown parameter '" + key + "' for '" + spec.head() + "'", line.source(),
                        "valid parameters: " + String.join(", ", validNames));
            }
        }
        boolean missingRequired = false;
        for (Param p : params) {
            if (p.required() && !named.containsKey(p.name())) {
                diags.error("E_MISSING_ARG",
                        "missing required parameter '" + p.name() + "' (" + p.type().label() + ") for '"
                                + spec.head() + "'", line.source(), "usage: " + spec.usage());
                missingRequired = true;
            }
        }
        // Skip the positional parse so toPositional's fabricated "" doesn't raise a second (type) error.
        if (missingRequired) {
            return Args.empty();
        }
        return spec.parse(spec.toPositional(named), line.source(), diags);
    }
}
