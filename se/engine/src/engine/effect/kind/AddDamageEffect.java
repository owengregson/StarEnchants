package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code ADD_DAMAGE} — boost the activator's outgoing damage by a percentage
 * (docs/architecture.md §7). Stateless; contributes a delta to the additive attack
 * bucket of the damage arbiter (§6.1) and never reads or sets the event damage
 * itself. {@link Affinity#CONTEXT_LOCAL}: it applies on the firing thread.
 */
public final class AddDamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ADD_DAMAGE")
            .param("percent", D.DOUBLE.min(0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Increase the activator's outgoing damage by a percentage (additive, §6.1).")
            .example("ADD_DAMAGE:25")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.addOutgoingDamage(ctx.dbl("percent") / 100.0);
    }
}
