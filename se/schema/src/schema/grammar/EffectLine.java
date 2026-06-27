package schema.grammar;

import schema.diag.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Untyped parsed shape of one effect line (docs/architecture.md §2): {@code head},
 * colon-separated {@code args}, and an optional trailing target {@code selector}. A final
 * colon-segment beginning with {@code @} is the selector ({@code DAMAGE:6:@Nearest{r=4}}).
 *
 * <p>The colon/terse form ({@link #parse}) is no longer an authorable content syntax — authored
 * effects are the block {@code HEAD: { param: value, who: }} form ({@link #verbose}). {@code parse}
 * survives only for the migrator, which reads a terse-like SE token while importing AE/EA/EE configs
 * and re-renders it as a block map. The content loader rejects terse scalars with {@code E_TERSE_EFFECT}.
 */
public record EffectLine(String head, int headCol, List<Tok> args, Tok selector, Source source,
                         Map<String, String> named) {

    public EffectLine {
        args = List.copyOf(args);
        named = named == null ? null : Map.copyOf(named);
    }

    /** Head is the first top-level colon-segment; a final {@code @}-segment peels off as the selector. */
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
     * Build a verbose effect line (ADR-0016). Values are NOT joined on {@code :}, so colon-
     * or {@code @}-bearing string args survive intact; the selector is set directly, never re-detected.
     */
    public static EffectLine verbose(String head, int headCol, Map<String, String> named,
                                     String selectorToken, Source source) {
        Tok selector = selectorToken == null ? null : new Tok(selectorToken, headCol);
        return new EffectLine(head, headCol, List.of(), selector, source, named);
    }

    /** A {@code WAIT <ticks>} line built directly, without the terse colon parser (the {@code wait:} desugar). */
    public static EffectLine waitLine(String ticks, Source source) {
        return new EffectLine("WAIT", 1, List.of(new Tok(ticks, 1)), null, source, null);
    }

    public boolean isVerbose() {
        return named != null;
    }

    public List<String> argTexts() {
        return args.stream().map(Tok::trimmed).toList();
    }

    public int argCount() {
        return args.size();
    }

    public Source sourceOfArg(int i) {
        return source.atColumn(args.get(i).col());
    }

    public Optional<String> selectorToken() {
        return selector == null ? Optional.empty() : Optional.of(selector.trimmed());
    }

    /** Selector position, or the line source when none was written. */
    public Source selectorSource() {
        return selector == null ? source : source.atColumn(selector.col());
    }
}
