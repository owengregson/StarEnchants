package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code FLAT_DAMAGE} — add a flat amount to the damage fold (docs/architecture.md §7).
 * Stateless; contributes a flat delta to the damage arbiter (heroic flat stats, §6.1)
 * rather than touching an entity directly. {@link Affinity#CONTEXT_LOCAL}: it applies on
 * the firing thread.
 */
public final class FlatDamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FLAT_DAMAGE")
            .param("amount", D.DOUBLE.min(0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Add a flat amount to the damage fold (heroic flat stats, §6.1).")
            .example("FLAT_DAMAGE:2")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.addFlatDamage(ctx.dbl("amount"));
    }
}
