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

/** {@code INVERT_VAR} — numerically flip a per-player named variable (§A); companion to {@link SetVarEffect} for toggling without a read. */
public final class InvertVarEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("INVERT_VAR")
            .param("name", D.STRING)
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Numerically invert a per-player variable (0↔1), preserving its remaining TTL.")
            .example("INVERT_VAR:rage:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String name = ctx.str("name");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.invertVar(p, name);
            }
        }
    }
}
