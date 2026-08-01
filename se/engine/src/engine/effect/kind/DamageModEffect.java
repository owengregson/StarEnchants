package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code DAMAGE_MOD} — canonical damage-arbiter primitive: one parameterized contribution to the additive
 * fold (ADR-0012, §6.1). Add mode takes a 0-100 percentage, flat takes a raw amount, and multiply
 * takes the exact scalar applied to the triggering damage (for example 0.925).
 */
public final class DamageModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE_MOD")
            .param("side", D.enumOf("attack", "defense"))
            .param("mode", D.enumOf("add", "flat", "multiply").def("add"))
            .param("amount", D.DOUBLE)
            .param("cap", D.DOUBLE.min(0).def(0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Contribute to the damage fold: side attack/defense; mode add uses a percentage, flat a raw "
                    + "amount, and multiply an exact direct factor. A NEGATIVE add amount is a self-nerf. "
                    + "cap clamps a positive evaluated amount when greater than zero (0 = uncapped).")
            .example("{ DAMAGE_MOD: { side: attack, mode: add, amount: 25, cap: 75 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        double cap = ctx.args().has("cap") ? ctx.dbl("cap") : 0.0;
        if (cap > 0.0 && amount > cap) {
            amount = cap;
        }
        boolean defense = "defense".equalsIgnoreCase(ctx.str("side"));
        String mode = ctx.str("mode");
        boolean flat = "flat".equalsIgnoreCase(mode);
        boolean multiply = "multiply".equalsIgnoreCase(mode);
        if (defense) {
            if (multiply) {
                sink.multiplyIncomingDamage(amount);
            } else if (flat) {
                sink.addFlatReduction(amount);
            } else {
                sink.addDamageReduction(amount / 100.0);
            }
        } else if (multiply) {
            sink.multiplyOutgoingDamage(amount);
        } else if (flat) {
            sink.addFlatDamage(amount);
        } else {
            sink.addOutgoingDamage(amount / 100.0);
        }
    }
}
