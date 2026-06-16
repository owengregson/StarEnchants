package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code PARTICLE} — spawn a burst of particles at the activation location
 * (docs/architecture.md §7). Stateless; emits one {@code particle} intent at the
 * activation centre and never touches the world directly. No-op when the activation
 * has no location (e.g. a non-positional combat trigger), since {@code ctx.location()}
 * may be {@code null}. {@link Affinity#REGION}: the burst is placed on the thread
 * owning that location's region. The {@code particle} handle is authored as a token
 * and resolved to an interned id at compile time, so the runtime never sees a renamed
 * constant (§9).
 */
public final class ParticleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PARTICLE")
            .param("particle", D.particle())
            .param("count", D.INT.min(0).def(1))
            .affinity(Affinity.REGION)
            .doc("Spawn particles at the activation location. No-op if there is no location.")
            .example("PARTICLE:FLAME:20")
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
