package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code SMELT} — auto-smelt the block broken by the triggering MINE. An inline read-back like
 * {@code IGNORE_ARMOR}: sets a flag the MINE dispatcher reads after the gate walk to swap the raw drop.
 */
public final class SmeltEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SMELT")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Auto-smelt the block broken by this MINE activation (ore→ingot, sand→glass, …).")
            .example("{ SMELT: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.smelt();
    }
}
