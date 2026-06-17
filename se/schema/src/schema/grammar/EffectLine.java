package schema.grammar;

import schema.diag.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The parsed shape of one authored effect/condition line: a {@code head} (the kind
 * name, e.g. {@code DAMAGE}), its colon-separated {@code args} (each carrying its
 * source column), and an optional trailing target {@code selector}
 * ({@code @Head{...}}).
 *
 * <p>This is untyped data — no validation happens here (docs/architecture.md §2,
 * "produces untyped AST"). The compiler resolves the head to a
 * {@link schema.spec.ParamSpec} and validates {@link #argTexts} against it,
 * attaching argument-precise diagnostics via {@link #sourceOfArg}, and resolves the
 * selector against the registered selector kinds.
 *
 * <p><strong>Inline selector grammar.</strong> An effect may name an explicit target
 * by appending a selector as the final colon-segment when that segment begins with
 * {@code @} — e.g. {@code DAMAGE:6:@Nearest{r=4}} fires {@code DAMAGE:6} at the
 * nearest entity instead of the effect's declared default target. The {@code @}
 * marker is unambiguous because no scalar argument type starts with {@code @} (a
 * literal {@code @} in a string argument must be quoted). When no inline selector is
 * present the compiler uses the effect kind's declared default target
 * (docs/architecture.md §3.5, §7).
 */
public record EffectLine(String head, int headCol, List<Tok> args, Tok selector, Source source,
                         Map<String, String> named) {

    public EffectLine {
        args = List.copyOf(args);
        named = named == null ? null : Map.copyOf(named);
    }

    /**
     * Parse a raw line into head + argument tokens, peeling a trailing inline
     * selector. The head is the first top-level colon-separated segment; everything
     * after is an argument token, except a final segment beginning with {@code @},
     * which becomes the {@link #selector}.
     */
    public static EffectLine parse(String raw, Source source) {
        List<Tok> parts = Lexer.splitTop(raw, ':');
        Tok headTok = parts.get(0);
        List<Tok> rest = new ArrayList<>(parts.subList(1, parts.size()));
        Tok selector = null;
        if (!rest.isEmpty() && rest.get(rest.size() - 1).trimmed().startsWith("@")) {
            selector = rest.remove(rest.size() - 1);
        }
        return new EffectLine(headTok.trimmed(), headTok.col(), List.copyOf(rest), selector, source, null);
    }

    /**
     * Build a verbose effect line (ADR-0016): the {@code named} param values (in any order) plus an
     * optional explicit selector token (the {@code who:} value, e.g. {@code "@Attacker"}). The selector
     * is set directly, never re-detected from an {@code @}-prefixed argument, and the values are NOT
     * joined on {@code :} — so colon- or {@code @}-bearing string arguments survive intact.
     */
    public static EffectLine verbose(String head, int headCol, Map<String, String> named,
                                     String selectorToken, Source source) {
        Tok selector = selectorToken == null ? null : new Tok(selectorToken, headCol);
        return new EffectLine(head, headCol, List.of(), selector, source, named);
    }

    /** Whether this line carries verbose (named) arguments rather than terse positional ones. */
    public boolean isVerbose() {
        return named != null;
    }

    /** The trimmed text of each argument, ready to feed {@code ParamSpec.parse}. */
    public List<String> argTexts() {
        return args.stream().map(Tok::trimmed).toList();
    }

    public int argCount() {
        return args.size();
    }

    /** The source position of argument {@code i}, for an argument-precise diagnostic. */
    public Source sourceOfArg(int i) {
        return source.atColumn(args.get(i).col());
    }

    /** The inline selector token ({@code @Head{...}}) if the line declared one. */
    public Optional<String> selectorToken() {
        return selector == null ? Optional.empty() : Optional.of(selector.trimmed());
    }

    /** The source position of the inline selector, or the line source when none was written. */
    public Source selectorSource() {
        return selector == null ? source : source.atColumn(selector.col());
    }
}
