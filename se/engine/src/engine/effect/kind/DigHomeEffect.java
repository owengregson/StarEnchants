package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code DIG_HOME} — the mole-pet dig marker (ADR-0061): declares that this USE ability opens a
 * {@code window}-tick recall window with a {@code range}-block recall radius. Service-owned (the WATER_SPEED
 * rule): the pets cold path reads this effect's compiled args at its activation success point, captures the
 * digger's location into the per-player home store and owns the whole recall / teleblock / expiry flow —
 * {@link #run} emits nothing, so the source-erased engine never carries per-player teleport state.
 */
public final class DigHomeEffect implements EffectKind {

    public static final String HEAD = "DIG_HOME";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("window", D.TICKS.def(600))
            .param("range", D.DOUBLE.min(0).def(50))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark the activator's location as a temporary home for `window` ticks: the next right-click of "
                    + "the same pet within `range` blocks teleports the activator back and consumes the window. "
                    + "Pets only — the pets service owns the recall; this effect emits no intent of its own.")
            .example("{ DIG_HOME: { window: 600, range: 50 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Service-owned marker (the WATER_SPEED rule): PetService arms the home window from this effect's
        // compiled args at the activation success point — an engine-side write here would double-arm it.
    }
}
