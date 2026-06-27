package schema.spec;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The signature of one DSL kind: a head plus ordered typed {@link Param}s, optional
 * {@link CrossRule}s, a doc string, and an example. One declaration drives {@link #parse},
 * {@link #completions}, {@link #usage}, and {@link #toPositional}, so the four can't drift
 * (docs/architecture.md §7, §10). Immutable; build via {@link #of(String)}.
 */
public final class ParamSpec {

    private final String head;
    private final List<Param> params;
    private final List<CrossRule> crossRules;
    private final String doc;
    private final String example;

    private ParamSpec(String head, List<Param> params, List<CrossRule> crossRules,
                      String doc, String example) {
        this.head = head;
        this.params = List.copyOf(params);
        this.crossRules = List.copyOf(crossRules);
        this.doc = doc;
        this.example = example;
    }

    public static Builder of(String head) {
        return new Builder(head);
    }

    public String head() {
        return head;
    }

    public List<Param> params() {
        return params;
    }

    public String doc() {
        return doc;
    }

    public String example() {
        return example;
    }

    /**
     * Validate raw positional args into typed {@link Args}; never throws (everything is a diagnostic).
     * The result is only meaningful once the caller confirms {@code !diags.hasErrors()}.
     */
    public Args parse(List<String> rawArgs, Source source, Diagnostics diags) {
        Objects.requireNonNull(rawArgs, "rawArgs");
        Map<String, Object> values = new java.util.LinkedHashMap<>();

        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            if (i < rawArgs.size()) {
                p.type().parse(rawArgs.get(i), source, diags)
                        .ifPresent(v -> values.put(p.name(), v));
            } else if (p.required()) {
                diags.error("E_MISSING_ARG",
                        "missing required argument '" + p.name() + "' (" + p.type().label() + ")",
                        source, "usage: " + usage());
            } else {
                p.type().defaultRaw().ifPresent(def ->
                        p.type().parse(def, source, diags).ifPresent(v -> values.put(p.name(), v)));
            }
        }

        if (rawArgs.size() > params.size()) {
            int extra = rawArgs.size() - params.size();
            diags.warning("W_EXTRA_ARGS",
                    "ignored " + extra + " extra argument" + (extra == 1 ? "" : "s")
                            + "; '" + head + "' takes " + params.size(),
                    source, "usage: " + usage());
        }

        Args args = new Args(values);
        // Cross-rules only run on a clean parse, to avoid cascading noise off a single type error.
        if (!diags.hasErrors()) {
            for (CrossRule rule : crossRules) {
                rule.check(args, source, diags);
            }
        }
        return args;
    }

    /** Completion candidates for the argument at {@code argIndex} given a partial token. */
    public List<String> completions(int argIndex, String prefix) {
        if (argIndex < 0 || argIndex >= params.size()) {
            return List.of();
        }
        return params.get(argIndex).type().completions(prefix);
    }

    /**
     * The human-readable block signature, e.g.
     * {@code { SMITE: { chance: <double[0..100]>, radius: <double[0..]>, cooldown: <int[0..]=0> } }}.
     * Each param renders {@code name: <type>}; an optional param appends its default ({@code =0}).
     * (The terse colon form is no longer authorable — see {@code schema.grammar.EffectLine}.)
     */
    public String usage() {
        if (params.isEmpty()) {
            return "{ " + head + ": {} }";
        }
        StringBuilder sb = new StringBuilder("{ ").append(head).append(": {");
        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            String def = p.required() ? "" : p.type().defaultRaw().map(d -> "=" + d).orElse("");
            sb.append(i == 0 ? " " : ", ")
                    .append(p.name()).append(": <").append(p.type().label()).append(def).append('>');
        }
        return sb.append(" } }").toString();
    }

    /**
     * Re-order a legacy named-arg map into this spec's positional order (the migrator's hook, §10).
     * Absent params yield their default or an empty token, so the line round-trips back through {@link #parse}.
     */
    public List<String> toPositional(Map<String, String> named) {
        List<String> out = new ArrayList<>(params.size());
        for (Param p : params) {
            String v = named.get(p.name());
            if (v == null) {
                v = p.type().defaultRaw().orElse("");
            }
            out.add(v);
        }
        return out;
    }

    public static final class Builder {
        private final String head;
        private final List<Param> params = new ArrayList<>();
        private final List<CrossRule> crossRules = new ArrayList<>();
        private String doc = "";
        private String example = "";

        private Builder(String head) {
            this.head = Objects.requireNonNull(head, "head");
        }

        public Builder param(String name, ParamType type) {
            params.add(Param.of(name, type));
            return this;
        }

        public Builder param(String name, ParamType type, String doc) {
            params.add(Param.of(name, type, doc));
            return this;
        }

        public Builder rule(CrossRule rule) {
            crossRules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        public Builder doc(String doc) {
            this.doc = doc == null ? "" : doc;
            return this;
        }

        public Builder example(String example) {
            this.example = example == null ? "" : example;
            return this;
        }

        public ParamSpec build() {
            return new ParamSpec(head, params, crossRules, doc, example);
        }
    }
}
