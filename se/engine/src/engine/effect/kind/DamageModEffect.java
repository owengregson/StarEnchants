package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code DAMAGE_MOD} — canonical damage-arbiter primitive (docs/v3-directives.md §C, ADR-0012, §6.1).
 * One parameterized contribution to the additive fold:
 *
 * <ul>
 *   <li>{@code side=attack, mode=add} — outgoing-damage percent;</li>
 *   <li>{@code side=defense, mode=add} — damage-reduction percent;</li>
 *   <li>{@code side=attack, mode=flat} — flat damage bonus;</li>
 *   <li>{@code side=defense, mode=flat} — flat damage reduction.</li>
 * </ul>
 *
 * <p>No target slot; {@link Affinity#CONTEXT_LOCAL} (a fold contribution reads neither targets nor an
 * entity). Percent modes take a 0-100 value ({@code 25} = +25%); flat modes take a raw damage amount.
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
