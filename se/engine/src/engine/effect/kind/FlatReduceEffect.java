package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code FLAT_REDUCE} — subtract a flat amount from the damage fold's defense side
 * (docs/architecture.md §7) — the defensive counterpart of {@code FLAT_DAMAGE}.
 * Stateless; contributes a flat reduction to the damage arbiter (heroic flat stats,
 * §6.1) rather than touching an entity directly. The reduction is subtracted last in
 * the fold, so it absorbs exactly this amount regardless of percent context.
 * {@link Affinity#CONTEXT_LOCAL}: it applies on the firing thread.
 */
public final class FlatReduceEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FLAT_REDUCE")
            .param("amount", D.DOUBLE.min(0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Subtract a flat amount from incoming damage (heroic flat reduction, §6.1).")
            .example("FLAT_REDUCE:2")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.addFlatReduction(ctx.dbl("amount"));
    }
}
