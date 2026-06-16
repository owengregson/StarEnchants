package engine.spec;

import schema.spec.CrossRule;
import schema.spec.ParamSpec;
import schema.spec.ParamType;

/**
 * The self-describing signature of a selector kind (docs/architecture.md §7,
 * "same pattern for {@code ConditionFn}, {@code TriggerKind}, {@code SelectorKind}").
 * A selector resolves an effect's target set at activation time; its {@code SPEC}
 * declares the named arguments it accepts (e.g. {@code @Aoe{r=4}}).
 *
 * <p>Unlike {@link EffectSpec} a selector carries no {@link compile.model.Affinity}
 * (routing is decided by the <em>effect's</em> affinity) and no nested target slots
 * (a selector <em>is</em> a target). It is a thin wrapper over the schema's
 * {@link ParamSpec} so a selector argument is validated, completed, documented and
 * migrated by exactly the same "one declaration, four uses" machinery as an effect
 * argument.
 *
 * <p>Builtin selectors used as an effect's <em>default</em> target should keep every
 * argument optional (give it a {@code def(...)}), so the no-argument default path
 * validates cleanly; an author who wants a non-default value writes it inline.
 */
public final class SelectorSpec {

    private final ParamSpec paramSpec;

    private SelectorSpec(ParamSpec paramSpec) {
        this.paramSpec = paramSpec;
    }

    public static Builder of(String head) {
        return new Builder(head);
    }

    /** The canonical head, e.g. {@code AOE}. */
    public String head() {
        return paramSpec.head();
    }

    /** The underlying argument signature (validation / completion / docs / migration). */
    public ParamSpec paramSpec() {
        return paramSpec;
    }

    public String doc() {
        return paramSpec.doc();
    }

    public String example() {
        return paramSpec.example();
    }

    /** Fluent builder delegating the argument signature to {@link ParamSpec.Builder}. */
    public static final class Builder {

        private final ParamSpec.Builder paramSpec;

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

        public Builder doc(String doc) {
            paramSpec.doc(doc);
            return this;
        }

        public Builder example(String example) {
            paramSpec.example(example);
            return this;
        }

        public SelectorSpec build() {
            return new SelectorSpec(paramSpec.build());
        }
    }
}
