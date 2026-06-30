package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code MARK} — mark the target(s) so the ACTOR deals an extra {@code amount}% damage to them for
 * {@code duration} (reaper's Mark of the Reaper, +25% from the reaper). The bonus is applied by the damage
 * fold's per-(victim, marker) consult in {@code CombatDispatch}, read before the attacker's abilities run — so
 * the marking hit itself is excluded. An inline per-pair flag write (no entity hop), like {@code SET_VAR}.
 */
public final class MarkEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MARK")
            .param("amount", D.DOUBLE)
            .param("duration", D.TICKS.def(60))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark the target(s) so the actor deals an extra `amount`% damage to them for `duration` ticks. "
                    + "Applied by the damage fold on the actor's later hits; default target the combat victim.")
            .example("{ MARK: { amount: 25, duration: 60, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        UUID marker = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        if (marker == null) {
            return;
        }
        double percent = ctx.dbl("amount");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.mark(target, marker, percent, duration);
        }
    }
}
