package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** Mark targets so later Poison/Wither damage is multiplied for a timed window. */
public final class VirusMarkEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("VIRUS_MARK")
            .param("multiplier", D.DOUBLE.min(0))
            .param("duration", D.TICKS)
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Multiply subsequent Poison and Wither damage to each target for the given duration.")
            .example("{ VIRUS_MARK: { multiplier: 3, duration: 60, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.markVirus(target, ctx.dbl("multiplier"), ctx.integer("duration"));
        }
    }
}
