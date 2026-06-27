package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.Param;
import schema.spec.ParamSpec;

/**
 * Renders a migrated SE effect token as a content-format-v2 effects-list item (ADR-0016): always the
 * block {@code { HEAD: { param: value, who: "@Sel" } }} map (the terse string form is no longer an
 * authorable content syntax, so migrated output must be block to reload). Positional args map onto the
 * spec's params by order; an inline {@code @Selector} becomes the reserved {@code who:} key. Returns
 * {@code null} when the head has no known {@link ParamSpec}, so the caller emits a {@code # TODO}
 * comment rather than an unreloadable effect.
 */
final class V2Effects {

    private V2Effects() {
    }

    static String item(String seToken, Function<String, ParamSpec> specs) {
        EffectLine line = EffectLine.parse(seToken, Source.ofFile("migrate"));
        String head = line.head();
        List<String> args = line.argTexts();
        String selector = line.selectorToken().orElse(null);

        // WAIT is a timing directive, not a param effect: the v2 block form is the scalar map { WAIT: <ticks> }.
        if (head.equalsIgnoreCase("WAIT")) {
            return "{ WAIT: " + (args.isEmpty() ? "0" : scalar(args.get(0))) + " }";
        }

        ParamSpec spec = specs == null ? null : specs.apply(head);
        if (spec == null) {
            return null; // unknown head → SchemaWriter emits a # TODO (never an unreloadable terse string)
        }

        // Any extra positional args beyond the spec are dropped (the compiler would warn-and-ignore them anyway).
        int n = Math.min(args.size(), spec.params().size());
        List<String> kvs = new ArrayList<>(n + 1);
        for (int i = 0; i < n; i++) {
            Param param = spec.params().get(i);
            kvs.add(param.name() + ": " + scalar(args.get(i)));
        }
        if (selector != null) {
            kvs.add("who: " + q(selector));
        }
        return "{ " + head + ": {" + (kvs.isEmpty() ? "}" : " " + String.join(", ", kvs) + " }") + " }";
    }

    /** A YAML flow scalar: bare for a plain integer/decimal, else a double-quoted string. */
    private static String scalar(String value) {
        return value.matches("-?\\d+(\\.\\d+)?") ? value : q(value);
    }

    /** A YAML double-quoted scalar — escapes backslash/quote and control chars a legacy token may carry. */
    private static String q(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r") + '"';
    }
}
