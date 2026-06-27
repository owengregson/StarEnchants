package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code SEEK} — make the BOW_FIRE projectile home onto the nearest line-of-sight target. An inline read-back
 * like {@code IGNORE_ARMOR}: sets a flag the bow dispatcher reads after the gate walk to start a steering task.
 */
public final class SeekEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SEEK")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the projectile fired by this BOW_FIRE activation home onto the nearest target in sight.")
            .example("{ SEEK: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.seek();
    }
}
