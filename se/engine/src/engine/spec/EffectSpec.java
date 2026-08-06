package engine.spec;

import compile.model.Affinity;
import schema.spec.CrossRule;
import schema.spec.D;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Self-describing signature of an effect kind — the SPI's central declaration (§7). Wraps the schema's
 * {@link ParamSpec} and adds the two facts a bare param signature lacks: the declared {@link Affinity}
 * (folded by the compiler to route work to the right thread, §3.6) and the {@link TargetSpec} slots the
 * effect reads.
 */
public final class EffectSpec {

    private final ParamSpec paramSpec;
    private final Affinity affinity;
    private final List<TargetSpec> targets;
    private final boolean needsActorOrigin;

    private EffectSpec(ParamSpec paramSpec, Affinity affinity, List<TargetSpec> targets, boolean needsActorOrigin) {
        this.paramSpec = paramSpec;
        this.affinity = affinity;
        this.targets = List.copyOf(targets);
        this.needsActorOrigin = needsActorOrigin;
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

    /** Whether run() anchors on the actor — the executor captures the ADR-0043 origin snapshot before running it. */
    public boolean needsActorOrigin() {
        return needsActorOrigin;
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
        private boolean needsActorOrigin;
        private boolean perTargetKnobsDeclared;

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

        /** Declare that run() anchors on the actor, so the executor captures its origin snapshot (ADR-0043). */
        public Builder actorOrigin() {
            this.needsActorOrigin = true;
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
            declarePerTargetKnobs();
            return new EffectSpec(paramSpec.build(), affinity, targets, needsActorOrigin);
        }

        /**
         * ADR-0076: declaring an ENTITY target slot implicitly declares the three per-target knobs, so all
         * ~140 registered kinds gain them without one kind being edited — and the ParamSpec
         * one-declaration-four-uses rule (validate / complete / {@code /se docs} / migrate) is satisfied by
         * this single declaration.
         *
         * <p>Appended in {@link #build()} rather than in {@link #target}, so they always land LAST whatever
         * order a kind declares its own params in — positional order is part of the authoring ABI.
         *
         * <p>{@link T#HERE} is the LOCATION analogue and is deliberately skipped: a block effect resolves
         * coordinates, and a subject cursor has no body to bind.
         */
        private void declarePerTargetKnobs() {
            boolean entitySlot = false;
            for (TargetSpec slot : targets) {
                entitySlot |= !T.HERE.equals(slot.selectorType());
            }
            if (!entitySlot || perTargetKnobsDeclared) {
                return;
            }
            perTargetKnobsDeclared = true;
            paramSpec.param("each-if", D.CONDITION.optional().hoisted(),
                    "Per-target filter: each resolved target is tested with the %target.*% subject bound, and "
                            + "a target that fails is dropped from THIS effect only. It cannot un-activate the "
                            + "ability, release its cooldown or refund its souls.");
            paramSpec.param("each-chance", D.DOUBLE.range(0, 100).optional().hoisted(),
                    "Per-target chance, sugar for each-if: \"%target.roll% < <this>\" over the ONE draw each "
                            + "body carries for the whole ability — so this row and its complement partition "
                            + "instead of rolling twice. Declaring each-if too ANDs them.");
            paramSpec.param("each-cooldown", D.TICKS.optional().hoisted(),
                    "Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the "
                            + "ability's cooldown scope, so declaring it without one is a load error.");
        }
    }
}
