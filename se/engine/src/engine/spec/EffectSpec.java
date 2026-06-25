package engine.spec;

import compile.model.Affinity;
import schema.spec.CrossRule;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Self-describing signature of an effect kind — the SPI's central declaration
 * (docs/architecture.md §7). Wraps the schema's {@link ParamSpec} (one declaration → validate /
 * complete / docs / migrate) and adds the two facts a bare param signature lacks:
 *
 * <ul>
 *   <li>the declared {@link Affinity} — folded by the compiler so the {@code Sink} routes work to the
 *       right thread and the author never schedules (§3.6);</li>
 *   <li>the {@link TargetSpec} slots the effect reads, each bound to a selector.</li>
 * </ul>
 *
 * <p>An effect kind exposes one as a {@code static final EffectSpec SPEC}; reaching affinity through
 * the registry is what keeps the compiler pure yet compile-time-aware of affinity.
 */
public final class EffectSpec {

    private final ParamSpec paramSpec;
    private final Affinity affinity;
    private final List<TargetSpec> targets;

    private EffectSpec(ParamSpec paramSpec, Affinity affinity, List<TargetSpec> targets) {
        this.paramSpec = paramSpec;
        this.affinity = affinity;
        this.targets = List.copyOf(targets);
    }

    public static Builder of(String head) {
        return new Builder(head);
    }

    /** The canonical head, e.g. {@code DAMAGE}. */
    public String head() {
        return paramSpec.head();
    }

    /** The underlying argument signature (validation / completion / docs / migration). */
    public ParamSpec paramSpec() {
        return paramSpec;
    }

    /** The declared dispatch affinity (§3.6). */
    public Affinity affinity() {
        return affinity;
    }

    /** The declared target slots, in declaration order. */
    public List<TargetSpec> targets() {
        return targets;
    }

    public String doc() {
        return paramSpec.doc();
    }

    public String example() {
        return paramSpec.example();
    }

    /**
     * Fluent builder. Affinity defaults to {@link Affinity#CONTEXT_LOCAL} (the zero-hop common case),
     * so an effect must opt in to wider routing.
     */
    public static final class Builder {

        private final ParamSpec.Builder paramSpec;
        private final List<TargetSpec> targets = new ArrayList<>();
        private Affinity affinity = Affinity.CONTEXT_LOCAL;

        private Builder(String head) {
            this.paramSpec = ParamSpec.of(head);
        }

        public Builder param(String name, ParamType type) {
            paramSpec.param(name, type);
            return this;
        }

        public Builder param(String name, ParamType type, String doc) {
            paramSpec.param(name, type, doc);
            return this;
        }

        public Builder rule(CrossRule rule) {
            paramSpec.rule(rule);
            return this;
        }

        public Builder target(String name, String selectorType) {
            targets.add(new TargetSpec(
                    Objects.requireNonNull(name, "name"),
                    Objects.requireNonNull(selectorType, "selectorType")));
            return this;
        }

        public Builder affinity(Affinity affinity) {
            this.affinity = Objects.requireNonNull(affinity, "affinity");
            return this;
        }

        public Builder doc(String doc) {
            paramSpec.doc(doc);
            return this;
        }

        public Builder example(String example) {
            paramSpec.example(example);
            return this;
        }

        public EffectSpec build() {
            return new EffectSpec(paramSpec.build(), affinity, targets);
        }
    }
}
