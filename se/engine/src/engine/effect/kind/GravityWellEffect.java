package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code GRAVITY_WELL} — the Singularity reforge marker (ADR-0071): declares the collapsing star the
 * reforge service spawns above the caster's selected block. Service-owned (the DIG_HOME rule): the
 * reforge cold path reads this effect's compiled args at its activation success point, resolves the
 * target block via the era raycast and owns the beam, the pull task and the implosion — {@link #run}
 * emits nothing, so the source-erased engine never carries per-well state.
 */
public final class GravityWellEffect implements EffectKind {

    public static final String HEAD = "GRAVITY_WELL";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("range", D.DOUBLE.min(1).def(12), "max block-selection distance (blocks)")
            .param("radius", D.DOUBLE.min(0.5).def(6), "pull + implosion radius around the core")
            .param("rise", D.DOUBLE.min(0).def(2.5), "core height above the selected block")
            .param("duration", D.TICKS.def(60), "pull phase length before the implosion")
            .param("period", D.INT.range(1, 20).def(2), "pull cadence in ticks")
            .param("pull", D.DOUBLE.min(0).def(0.28), "per-pulse velocity magnitude toward the core")
            .param("damage", D.DOUBLE.min(0).def(8.0), "implosion damage at the core, RAW health-space (8.0 = 4 hearts)")
            .param("falloff-floor", D.DOUBLE.range(0, 1).def(0.25), "damage fraction kept at the radius edge")
            .param("self-pull", D.BOOL.def(true), "the caster is dragged too (the authored downside)")
            .param("self-damage", D.BOOL.def(true), "the implosion hits the caster too if inside")
            .param("r", D.INT.range(0, 255).def(190))
            .param("g", D.INT.range(0, 255).def(120))
            .param("b", D.INT.range(0, 255).def(255))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Singularity (reforges): throw a particle beam onto the block in your sights (max range); a "
                    + "collapsing star forms rise blocks above it, drags every living thing within radius toward "
                    + "the core for duration ticks, then implodes for damage (linear falloff to falloff-floor at "
                    + "the edge). Pulls — and hurts — the caster too unless self-pull/self-damage are off. "
                    + "Reforge-service-owned: this effect emits no intent of its own.")
            .example("{ GRAVITY_WELL: { range: 12, radius: 6, duration: 60, damage: 8.0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Service-owned marker (the DIG_HOME rule): GravityWellService arms the well from this effect's
        // compiled args at the reforge activation success point — an engine-side write here would double-arm.
    }
}
