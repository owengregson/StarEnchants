package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code DAMAGE_MOD} — canonical damage-arbiter primitive: one parameterized contribution to the additive
 * fold (ADR-0012, §6.1). Percent modes take a 0-100 value ({@code 25} = +25%); flat modes take a raw amount.
 */
public final class DamageModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE_MOD")
            .param("side", D.enumOf("attack", "defense"))
            .param("mode", D.enumOf("add", "flat").def("add"))
            .param("amount", D.DOUBLE)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Contribute to the damage fold: side attack/defense, mode add (percent) or flat (raw amount). "
                    + "A NEGATIVE amount is a self-nerf — attack:add:-50 halves your own outgoing damage. "
                    + "Replaces ADD_DAMAGE/REDUCE_DAMAGE/FLAT_DAMAGE/FLAT_REDUCE.")
            .example("DAMAGE_MOD:attack:add:25")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        boolean defense = "defense".equalsIgnoreCase(ctx.str("side"));
        boolean flat = "flat".equalsIgnoreCase(ctx.str("mode"));
        if (defense) {
            if (flat) {
                sink.addFlatReduction(amount);
            } else {
                sink.addDamageReduction(amount / 100.0);
            }
        } else if (flat) {
            sink.addFlatDamage(amount);
        } else {
            sink.addOutgoingDamage(amount / 100.0);
        }
    }
}
