package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code DAMAGE_SCALE} — a count-scaled contribution to the damage fold (§6.1): {@code per} for each resolved
 * target in the {@code who} set, optionally clamped to {@code cap}, routed exactly like {@link DamageModEffect}
 * (add = percent / 100 into the additive bucket; flat = raw amount). KOTH's Victorious scales +10% outgoing
 * per nearby player; any "stronger in a crowd / per stacked mob" behaviour reuses it as pure YAML. The count
 * is the selector's pre-resolved set — the world read lives in the selector, never here — so a no-match set
 * contributes nothing (a clean zero, not a fold of garbage).
 */
public final class DamageScaleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE_SCALE")
            .param("side", D.enumOf("attack", "defense").def("attack"))
            .param("mode", D.enumOf("add", "flat").def("add"))
            .param("per", D.DOUBLE)
            .param("cap", D.DOUBLE.min(0).def(0))
            .target("who", T.AOE)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Contribute per resolved target in 'who' to the damage fold: total = per * count, clamped to "
                    + "cap (0 = uncapped). side attack/defense, mode add (percent, e.g. 10 = +10% each) or flat "
                    + "(raw). The count is the selector's resolved set, e.g. who: @AllPlayers{r=7}.")
            .example("{ DAMAGE_SCALE: { side: attack, mode: add, per: 10, cap: 100, who: \"@AllPlayers{r=7}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int count = 0;
        java.util.Iterator<LivingEntity> it = ctx.targets("who").iterator();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        if (count == 0) {
            return;
        }
        double total = ctx.dbl("per") * count;
        double cap = ctx.dbl("cap");
        if (cap > 0 && total > cap) {
            total = cap;
        }
        boolean defense = "defense".equalsIgnoreCase(ctx.str("side"));
        boolean flat = "flat".equalsIgnoreCase(ctx.str("mode"));
        if (defense) {
            if (flat) {
                sink.addFlatReduction(total);
            } else {
                sink.addDamageReduction(total / 100.0);
            }
        } else if (flat) {
            sink.addFlatDamage(total);
        } else {
            sink.addOutgoingDamage(total / 100.0);
        }
    }
}
