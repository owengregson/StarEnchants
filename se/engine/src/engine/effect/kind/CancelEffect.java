package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;

/** {@code CANCEL} — cancel the Bukkit event that triggered this activation (§7). */
public final class CancelEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CANCEL")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Cancel the Bukkit event that triggered this activation.")
            .example("{ CANCEL: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.cancelEvent();
    }
}
