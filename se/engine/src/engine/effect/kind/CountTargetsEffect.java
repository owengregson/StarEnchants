package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code COUNT_TARGETS} — count one resolved selector and cache the integer on the activating player.
 * The value is readable by later activations as {@code %name%}; a repeating ability can therefore sample
 * an area at a source-accurate cadence while combat abilities consume the last sample without rescanning.
 */
public final class CountTargetsEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("COUNT_TARGETS")
            .param("name", D.STRING)
            .param("ttl", D.TICKS.def(0))
            .target("who", T.AOE)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Count resolved targets and store the integer on the actor as %name% (ttl ticks, 0 = forever).")
            .example("{ COUNT_TARGETS: { name: nearby-allies, ttl: 121, who: \"@Aoe{r=7,filter=ALLIES}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        int count = 0;
        for (LivingEntity ignored : ctx.targets("who")) {
            count++;
        }
        sink.setVar(actor, ctx.str("name"), Integer.toString(count), ctx.integer("ttl"));
    }
}
