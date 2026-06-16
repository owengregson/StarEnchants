package schema.grammar;

import schema.diag.Source;
import java.util.List;

/**
 * The parsed shape of one authored effect/condition line: a {@code head} (the
 * kind name, e.g. {@code DAMAGE}) and its colon-separated {@code args}, each
 * carrying its source column.
 *
 * <p>This is untyped data — no validation happens here (docs/architecture.md §2,
 * "produces untyped AST"). The compiler resolves the head to a
 * {@link schema.spec.ParamSpec} and validates {@link #argTexts}
 * against it, attaching argument-precise diagnostics via {@link #sourceOfArg}.
 */
public record EffectLine(String head, int headCol, List<Tok> args, Source source) {

    /**
     * Parse a raw line into head + argument tokens. The head is the first
     * top-level colon-separated segment; everything after is an argument token.
     */
    public static EffectLine parse(String raw, Source source) {
        List<Tok> parts = Lexer.splitTop(raw, ':');
        Tok headTok = parts.get(0);
        List<Tok> args = List.copyOf(parts.subList(1, parts.size()));
        return new EffectLine(headTok.trimmed(), headTok.col(), args, source);
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
}
