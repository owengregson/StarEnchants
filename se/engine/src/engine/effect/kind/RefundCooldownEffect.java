package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code REFUND_COOLDOWN} — hand back the cooldown gate 6 reserved for THIS ability, unless {@code unless}
 * evaluates non-zero at the moment the effect runs (R-QC15).
 *
 * <p>Gate 6 reserves before any effect runs, which is right for every refusal a condition can state. Some
 * cannot: an inventory conversion has to RUN before anyone knows it converted nothing, and by then the window
 * is already armed — the elemental pets charged a five-minute cooldown for a click with no empty buckets.
 * Authored AFTER the payload in the same ability, this is the post-payload branch that gives it back.
 *
 * <p>The knob is spelled {@code unless} rather than {@code when} because the fact a payload leaves behind is a
 * COUNT, and a numeric argument takes arithmetic, not a comparison: {@code unless: "%lavapet.filled%"} reads
 * "refund unless something was converted" with no arithmetic gymnastics.
 *
 * <p>Deliberately scoped to the ability's own reservation and nothing else: the effect takes no key, so it can
 * neither name nor release a window another ability owns. {@code CONTEXT_LOCAL}, so it runs inline in the tick
 * the gate reserved — the release is value-matched on that tick's expiry.
 */
public final class RefundCooldownEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REFUND_COOLDOWN")
            .param("unless", D.DOUBLE.def(0),
                    "skip the refund when this evaluates non-zero; the default (0) always refunds")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Hand back the cooldown this ability's own gate-6 reservation armed, unless `unless` evaluates "
                    + "non-zero. Author it AFTER the payload whose outcome decides the refusal — a condition "
                    + "cannot, because the fact it would read does not exist until the payload has run. It can "
                    + "only ever release THIS ability's window, and only in the tick the gate reserved it, so a "
                    + "WAIT tier between the payload and the refund silently forfeits it.")
            .example("{ REFUND_COOLDOWN: { unless: \"%lavapet.filled%\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        if (ctx.dbl("unless") != 0.0) {
            return; // the payload found something to do — the window stands
        }
        sink.refundCooldown(ctx.actor(), ctx.cooldownScope(), ctx.cooldownTicks());
    }
}
