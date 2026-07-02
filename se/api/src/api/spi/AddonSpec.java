package api.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import schema.spec.ParamSpec;
import schema.spec.ParamType;

/**
 * The self-describing signature of an {@link AddonEffect} — the add-on-facing counterpart of the engine's
 * {@code EffectSpec} (docs/architecture.md §7). It is built ON the schema's {@link ParamSpec} (the DSL's
 * documented four-ways single source: validation, completion, {@code /se docs}, migration), plus the two
 * facts a bare param signature lacks and that live outside {@code :schema}: the declared {@link AddonAffinity}
 * and the target selector slots. The bootstrap adapter reads these back to build the engine's own spec.
 *
 * <p>Immutable; build via {@link #of(String)}. Types come from {@code schema.spec.D} (e.g.
 * {@code D.DOUBLE.min(0).max(100)}, {@code D.material()}), so an add-on declares its arguments with the same
 * vocabulary the built-in effects do.
 */
public final class AddonSpec {

    private final ParamSpec paramSpec;
    private final AddonAffinity affinity;
    private final List<AddonTarget> targets;

    private AddonSpec(ParamSpec paramSpec, AddonAffinity affinity, List<AddonTarget> targets) {
        this.paramSpec = paramSpec;
        this.affinity = affinity;
        this.targets = List.copyOf(targets);
    }

    public static Builder of(String head) {
        return new Builder(head);
    }

    /** The canonical head this effect registers under, e.g. {@code MY_ADDON_BOLT}. */
    public String head() {
        return paramSpec.head();
    }

    /** The schema argument signature (validation / completion / docs / migration). */
    public ParamSpec paramSpec() {
        return paramSpec;
    }

    /** The declared dispatch affinity (§3.6). */
    public AddonAffinity affinity() {
        return affinity;
    }

    /** The declared target selector slots, in declaration order. */
    public List<AddonTarget> targets() {
        return targets;
    }

    public String doc() {
        return paramSpec.doc();
    }

    public String example() {
        return paramSpec.example();
    }

    /**
     * One target selector slot: a named slot filled by a selector of {@code selectorType} (e.g. {@code @Self},
     * {@code @Victim}, {@code @AOE}), read back through {@link AddonEffectCtx#targets(String)}.
     */
    public record AddonTarget(String name, String selectorType) {
        public AddonTarget {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(selectorType, "selectorType");
        }
    }

    /**
     * Fluent builder. Affinity defaults to {@link AddonAffinity#CONTEXT_LOCAL} (the zero-hop common case), so
     * an effect must opt in to wider routing — the same default the engine's builder applies.
     */
    public static final class Builder {

        private final ParamSpec.Builder paramSpec;
        private final List<AddonTarget> targets = new ArrayList<>();
        private AddonAffinity affinity = AddonAffinity.CONTEXT_LOCAL;

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

        public Builder target(String name, String selectorType) {
            targets.add(new AddonTarget(name, selectorType));
            return this;
        }

        public Builder affinity(AddonAffinity affinity) {
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

        public AddonSpec build() {
            return new AddonSpec(paramSpec.build(), affinity, targets);
        }
    }
}
