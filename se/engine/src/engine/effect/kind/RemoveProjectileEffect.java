package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/** {@code REMOVE_PROJECTILE} — delete the raw projectile responsible for the current impact event. */
public final class RemoveProjectileEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_PROJECTILE")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Request deletion of the raw projectile that caused the current damage event.")
            .example("{ REMOVE_PROJECTILE: {} }")
            .build();

    @Override public EffectSpec spec() { return SPEC; }

    @Override public void run(EffectCtx ctx, Sink sink) {
        sink.removeTriggeringProjectile();
    }
}
