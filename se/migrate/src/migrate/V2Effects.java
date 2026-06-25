package migrate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.Param;
import schema.spec.ParamSpec;

/**
 * Renders a migrated SE effect token as a content-format-v2 effects-list item (ADR-0016): the verbose
 * {@code { HEAD: { param: value, who: "@Sel" } }} map when the head's {@link ParamSpec} is known, else a
 * quoted terse string as a safe fallback. Positional args map onto the spec's params by order; an inline
 * {@code @Selector} becomes the reserved {@code who:} key.
 */
final class V2Effects {

    private V2Effects() {
    }

    static String item(String seToken, Function<String, ParamSpec> specs) {
        EffectLine line = EffectLine.parse(seToken, Source.ofFile("migrate"));
        String head = line.head();
        List<String> args = line.argTexts();
        String selector = line.selectorToken().orElse(null);
        ParamSpec spec = specs == null ? null : specs.apply(head);

        // No spec / WAIT timing directive / more args than params → keep the terse form (still valid v2).
        if (spec == null || head.equalsIgnoreCase("WAIT") || args.size() > spec.params().size()) {
            return q(seToken);
        }

        List<String> kvs = new ArrayList<>(args.size() + 1);
        for (int i = 0; i < args.size(); i++) {
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
