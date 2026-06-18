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
 * {@code SET_VAR} — set a per-player named variable (docs/v3-directives.md §A, AE parameterized vars). The
 * value is readable in any later condition as {@code %name%} (the read side rides the unknown-token/PAPI
 * seam, resolving the {@code VarStore} before real PAPI). {@code ttl} bounds its lifetime in ticks
 * ({@code 0} = no expiry). The {@code who} selector picks whose variable is set (default the activator).
 * {@link Affinity#CONTEXT_LOCAL}: per-player in-memory state, no world mutation (the sink writes the
 * concurrent store directly after capturing the target's UUID).
 */
public final class SetVarEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SET_VAR")
            .param("name", D.STRING)
            .param("value", D.STRING.def(""))
            .param("ttl", D.TICKS.def(0))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Set a per-player variable readable in later conditions as %name% (ttl ticks, 0 = forever).")
            .example("SET_VAR:rage:1:200:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String name = ctx.str("name");
        String value = ctx.str("value");
        int ttl = ctx.integer("ttl");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.setVar(p, name, value, ttl);
            }
        }
    }
}
