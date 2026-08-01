package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** Mark a living target so its eventual death XP is multiplied on a named channel. */
public final class ExpDropMarkEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXP_DROP_MARK")
            .param("channel", D.STRING)
            .param("multiplier", D.DOUBLE.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark each target with a named death-XP multiplier. Re-marking replaces that channel; distinct channels multiply.")
            .example("{ EXP_DROP_MARK: { channel: inquisitive, multiplier: 1.5, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.markExpDrop(target, ctx.str("channel"), ctx.dbl("multiplier"));
        }
    }
}
