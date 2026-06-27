package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/** {@code PARTICLE} — spawn a burst of particles at the activation location (§7); {@code particle} interned at compile (§9). */
public final class ParticleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PARTICLE")
            .param("particle", D.particle())
            .param("count", D.INT.min(0).def(1))
            .affinity(Affinity.REGION)
            .doc("Spawn particles at the activation location. No-op if there is no location.")
            .example("{ PARTICLE: { particle: FLAME, count: 20 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        org.bukkit.Location loc = ctx.location();
        if (loc != null) {
            sink.particle(loc, ctx.integer("particle"), ctx.integer("count"));
        }
    }
}
