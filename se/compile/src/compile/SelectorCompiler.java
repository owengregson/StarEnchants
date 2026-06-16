package compile;

import compile.model.CompiledSelector;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.sel.SelectorAst;
import schema.spec.Args;
import schema.spec.Param;
import schema.spec.ParamSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates a target selector against its registered {@link ParamSpec} and lowers it
 * to a {@link CompiledSelector} (docs/architecture.md §3.5, §7). Two entry points:
 * an <em>inline</em> selector an author wrote on an effect line ({@code @Aoe{r=4}}),
 * and the <em>default</em> selector an effect kind declares for a target slot when no
 * inline selector is present.
 *
 * <p>Selector arguments are authored as named {@code k=v} pairs; this re-orders them
 * into the spec's positional order with {@link ParamSpec#toPositional} (the same
 * machinery the migrator uses) and then validates with {@link ParamSpec#parse}, so a
 * selector argument gets the identical type/range checking and diagnostics as an
 * effect argument — no second validator to drift.
 *
 * <p>Never throws: an unknown selector head, a malformed argument, or an unknown
 * argument name is a file/line {@link schema.diag.Diagnostic}; on a hard failure the
 * effect falls back to {@link CompiledSelector#SELF} so one bad selector yields one
 * finding rather than aborting the load (§7, §10).
 */
public final class SelectorCompiler {

    private final SpecRegistry selectors;

    public SelectorCompiler(SpecRegistry selectors) {
        this.selectors = Objects.requireNonNull(selectors, "selectors");
    }

    /**
     * Compile an inline selector token ({@code @Head{k=v}}) into a
     * {@link CompiledSelector}, returning {@link CompiledSelector#SELF} on a hard
     * syntax or resolution failure (already diagnosed).
     */
    public CompiledSelector compileInline(String token, Source source, Diagnostics diags) {
        return SelectorAst.parse(token, source, diags)
                .map(ast -> compile(ast, source, diags))
                .orElse(CompiledSelector.SELF);
    }

    /**
     * The default selector for an effect that wrote none: the kind's declared target
     * selector, or {@link CompiledSelector#SELF} when the kind declares no target.
     * The declared head is validated with all-default arguments, so a builtin default
     * selector must keep its arguments optional.
     */
    public CompiledSelector defaultFor(String selectorHead, Source source, Diagnostics diags) {
        if (selectorHead == null || selectorHead.isBlank()
                || CompiledSelector.SELF.head().equalsIgnoreCase(selectorHead)) {
            return CompiledSelector.SELF;
        }
        return compile(new SelectorAst(selectorHead, Map.of(), source), source, diags);
    }

    private CompiledSelector compile(SelectorAst ast, Source source, Diagnostics diags) {
        Optional<ParamSpec> found = selectors.lookup(ast.head());
        if (found.isEmpty()) {
            diags.error("E_UNKNOWN_SELECTOR", "unknown selector '@" + ast.head() + "'", source,
                    "run /se selectors to list available selectors");
            return CompiledSelector.SELF;
        }
        ParamSpec spec = found.get();
        warnUnknownArgs(ast, spec, source, diags);
        List<String> positional = spec.toPositional(ast.args());
        Args args = spec.parse(positional, source, diags);
        return new CompiledSelector(spec.head(), args);
    }

    /** Flag any authored selector argument the spec does not declare (a likely typo). */
    private static void warnUnknownArgs(SelectorAst ast, ParamSpec spec, Source source, Diagnostics diags) {
        Set<String> known = spec.params().stream().map(Param::name).collect(Collectors.toSet());
        for (String name : ast.args().keySet()) {
            if (!known.contains(name)) {
                diags.warning("W_SELECTOR_UNKNOWN_ARG",
                        "selector '@" + spec.head() + "' has no argument '" + name + "'; it is ignored",
                        source, "known arguments: " + (known.isEmpty() ? "(none)" : String.join(", ", known)));
            }
        }
    }
}
