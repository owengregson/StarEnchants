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
 * resolve the head to a {@link ParamSpec} → validate arguments into typed {@link Args}.
 *
 * <p>This is the smallest end-to-end slice of the content compiler and the seam
 * between {@code se-schema} (the typed language) and the rest of {@code se-compile}.
 * It never throws — an unknown head and every argument fault are reported as
 * file/line {@link schema.diag.Diagnostic}s
 * (docs/architecture.md §3.3, §10). An unknown head is warn-and-skipped (the op
 * is dropped, returns empty); a known head with bad arguments still returns the
 * line so the caller can decide, having checked {@link Diagnostics#hasErrors()}.
 *
 * <p><strong>Terse vs verbose (ADR-0016).</strong> A terse {@link EffectLine} carries positional
 * argument texts; a verbose one carries a {@code named} map. For the verbose form we order the named
 * values into this spec's positional order <em>as a list</em> (never by re-joining on {@code :}), so a
 * string argument containing {@code :} or {@code @} survives intact — then validate through the same
 * {@code ParamSpec.parse} path. Unknown param names and missing required params are named precisely.
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
     * Validate a verbose (named) line: reject unknown param names and missing required params with
     * effect- and param-precise diagnostics, then order the provided values into the spec's positional
     * order and run the normal {@link ParamSpec#parse} for typing/range. Values are placed as whole list
     * elements — a {@code :} or leading {@code @} inside a string value can never be re-split or mistaken
     * for a selector (the selector was set explicitly from {@code who:} on the {@link EffectLine}).
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
        // A required param is missing → its precise E_MISSING_ARG is already recorded; skip the positional
        // parse so toPositional's fabricated "" for that slot does not raise a confusing second (type) error.
        if (missingRequired) {
            return Args.empty();
        }
        // All required present: toPositional fills omitted optionals with their default, then parse types each.
        return spec.parse(spec.toPositional(named), line.source(), diags);
    }
}
