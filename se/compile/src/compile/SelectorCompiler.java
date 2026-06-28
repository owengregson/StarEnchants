package compile;

import compile.model.CompiledSelector;
import schema.diag.DiagCode;
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
 * Validates a target selector and lowers it to a {@link CompiledSelector} (docs/architecture.md §3.5, §7).
 * Two entry points: an inline selector ({@code @Aoe{r=4}}) and a kind's declared default.
 *
 * <p>Named {@code k=v} args reuse the effect-argument path ({@link ParamSpec#toPositional} then
 * {@link ParamSpec#parse}) so there's no second validator to drift.
 *
 * <p>Never throws: a bad selector head/argument is a diagnostic and the effect falls back to
 * {@link CompiledSelector#SELF}, so one bad selector doesn't abort the load (§7, §10).
 */
public final class SelectorCompiler {

    private final SpecRegistry selectors;

    public SelectorCompiler(SpecRegistry selectors) {
        this.selectors = Objects.requireNonNull(selectors, "selectors");
    }

    public CompiledSelector compileInline(String token, Source source, Diagnostics diags) {
        return SelectorAst.parse(token, source, diags)
                .map(ast -> compile(ast, source, diags))
                .orElse(CompiledSelector.SELF);
    }

    /** The kind's declared default selector (or {@code SELF}); validated with all-default args, so a builtin default selector must keep its args optional. */
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
            diags.error(DiagCode.E_UNKNOWN_SELECTOR, "unknown selector '@" + ast.head() + "'", source,
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
                diags.warning(DiagCode.W_SELECTOR_UNKNOWN_ARG,
                        "selector '@" + spec.head() + "' has no argument '" + name + "'; it is ignored",
                        source, "known arguments: " + (known.isEmpty() ? "(none)" : String.join(", ", known)));
            }
        }
    }
}
