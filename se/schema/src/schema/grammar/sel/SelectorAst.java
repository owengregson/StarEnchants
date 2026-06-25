package schema.grammar.sel;

import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.Lexer;
import schema.grammar.Tok;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Untyped parsed shape of a target-selector token: a {@code head} and its named {@code k=v}
 * arguments ({@code @Aoe{r=4}}). Only syntax errors are reported here; an unknown head or
 * out-of-range argument is the compiler's concern (docs/architecture.md §2). Named args (not
 * colon-positional) so selectors read terse and out of order; the compiler reorders them via
 * {@link schema.spec.ParamSpec#toPositional}.
 */
public record SelectorAst(String head, Map<String, String> args, Source source) {

    public SelectorAst {
        args = Map.copyOf(args);
    }

    /** Returns empty (and records a diagnostic) on a hard syntax error, so the caller falls back to self-target. */
    public static Optional<SelectorAst> parse(String raw, Source source, Diagnostics diags) {
        String token = raw == null ? "" : raw.trim();
        if (token.isEmpty() || token.charAt(0) != '@') {
            diags.error("E_SELECTOR_SYNTAX",
                    "a selector must start with '@' but got '" + token + "'",
                    source, "usage: @Head or @Head{k=v,k=v}, e.g. @Nearest{r=4}");
            return Optional.empty();
        }

        String body = token.substring(1);
        int brace = body.indexOf('{');
        if (brace < 0) {
            String head = body.trim();
            if (head.isEmpty()) {
                diags.error("E_SELECTOR_SYNTAX", "selector '" + token + "' has no name",
                        source, "usage: @Head or @Head{k=v}, e.g. @Self or @Aoe{r=4}");
                return Optional.empty();
            }
            return Optional.of(new SelectorAst(head, Map.of(), source));
        }

        if (!body.endsWith("}")) {
            diags.error("E_SELECTOR_SYNTAX",
                    "selector '" + token + "' is missing its closing '}'",
                    source, "usage: @Head{k=v,k=v}, e.g. @Aoe{r=4}");
            return Optional.empty();
        }
        String head = body.substring(0, brace).trim();
        if (head.isEmpty()) {
            diags.error("E_SELECTOR_SYNTAX", "selector '" + token + "' has no name before '{'",
                    source, "usage: @Head{k=v}, e.g. @Aoe{r=4}");
            return Optional.empty();
        }

        String inside = body.substring(brace + 1, body.length() - 1);
        Map<String, String> args = new LinkedHashMap<>();
        boolean ok = true;
        for (Tok pairTok : Lexer.splitTop(inside, ',')) {
            String pair = pairTok.trimmed();
            if (pair.isEmpty()) {
                continue; // tolerate a trailing or doubled comma
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                diags.error("E_SELECTOR_SYNTAX",
                        "selector argument '" + pair + "' must be written name=value",
                        source.atColumn(pairTok.col()), "e.g. r=4");
                ok = false;
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (key.isEmpty()) {
                diags.error("E_SELECTOR_SYNTAX", "selector argument '" + pair + "' has an empty name",
                        source.atColumn(pairTok.col()), "e.g. r=4");
                ok = false;
                continue;
            }
            if (args.put(key, value) != null) {
                diags.warning("W_SELECTOR_DUP_ARG",
                        "selector argument '" + key + "' is set more than once; the last value wins",
                        source.atColumn(pairTok.col()));
            }
        }
        return ok ? Optional.of(new SelectorAst(head, args, source)) : Optional.empty();
    }
}
