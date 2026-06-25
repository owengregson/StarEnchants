package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code SEEK} — make the projectile fired by the triggering BOW_FIRE home onto the nearest line-of-sight
 * target (the Cosmic Enchants-style {@code AUTO_LOCK} effect). An inline read-back like {@code IGNORE_ARMOR}: the proc sets a
 * flag the bow dispatcher reads after the gate walk and applies to the shot {@code Projectile}, starting a
 * per-projectile steering task on its own thread. Author on the BOW_FIRE trigger.
 * {@link Affinity#CONTEXT_LOCAL}.
 */
public final class SeekEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SEEK")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Make the projectile fired by this BOW_FIRE activation home onto the nearest target in sight.")
            .example("SEEK")
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
