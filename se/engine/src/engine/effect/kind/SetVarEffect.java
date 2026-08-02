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
 * {@code SET_VAR} — set a per-player named variable (§A), readable in later conditions as {@code %name%}: the
 * read side rides the unknown-token/PAPI seam, resolving the {@code VarStore} before real PAPI.
 */
public final class SetVarEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SET_VAR")
            .param("name", D.STRING)
            .param("value", D.STRING.def(""))
            .param("ttl", D.TICKS.def(0))
            .param("op", D.enumOf("set", "increment").def("set"))
            .param("step", D.INT.def(1))
            .param("cap", D.INT.min(0).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Set (or with op=increment, add to) a variable on the target, readable in later conditions "
                    + "as %name% on the activator or %victim.var.name% on the victim. ttl ticks, 0 = forever; "
                    + "cap 0 = uncapped. Any living entity can carry one, so a mob holds its own stacks.")
            .example("{ SET_VAR: { name: bleedstacks, op: increment, step: 1, cap: 20, ttl: 200, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String name = ctx.str("name");
        int ttl = ctx.integer("ttl");
        boolean increment = "increment".equalsIgnoreCase(ctx.str("op"));
        for (LivingEntity target : ctx.targets("who")) {
            if (increment) {
                sink.incrementVar(target, name, ctx.integer("step"), ctx.integer("cap"), ttl);
            } else {
                sink.setVarOn(target, name, ctx.str("value"), ttl);
            }
        }
    }
}
