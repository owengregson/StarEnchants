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
 * {@code INVERT_VAR} — numerically flip a per-player named variable (docs/v3-directives.md §A): {@code 0}
 * (or an unset/non-numeric value) becomes {@code 1}, any non-zero value becomes {@code 0}, preserving the
 * variable's remaining TTL. The companion to {@link SetVarEffect} for toggling a boolean-style flag without
 * reading its current value. The {@code who} selector picks whose variable is inverted (default the
 * activator). {@link Affinity#CONTEXT_LOCAL}: per-player in-memory state, no world mutation.
 */
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
