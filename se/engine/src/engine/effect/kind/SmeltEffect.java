package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * {@code SMELT} — auto-smelt the block broken by the triggering MINE (the EE {@code SMELT} effect): ores
 * yield their smelted ingot, sand yields glass, etc. An inline read-back like {@code IGNORE_ARMOR}: the proc
 * sets a flag the MINE dispatcher reads after the gate walk and applies to the {@code BlockBreakEvent} (drop
 * the smelted result, suppress the raw drop). Author on the MINE trigger. {@link Affinity#CONTEXT_LOCAL}.
 */
public final class SmeltEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SMELT")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Auto-smelt the block broken by this MINE activation (ore→ingot, sand→glass, …).")
            .example("SMELT")
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
