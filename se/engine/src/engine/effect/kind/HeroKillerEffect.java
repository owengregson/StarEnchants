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

/** {@code HERO_KILLER} — Cosmic's soul-gated heroic-armor axe multiplier. */
public final class HeroKillerEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("HERO_KILLER")
            .param("level", D.INT.min(1).max(3))
            .param("cost", D.INT.min(0).def(4))
            .param("cost-period", D.TICKS.def(20))
            .param("anti-swap", D.TICKS.def(5))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Require active soul mode, positive carried souls, a settled held slot, no Soul Trap, and a "
                    + "victim wearing heroic armor; charge four souls at most once per second and multiply the "
                    + "hit by 1 + 0.10 * level.")
            .example("{ HERO_KILLER: { level: 3 } }")
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
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player victim) {
                sink.heroKiller(actor, victim, ctx.integer("level"), HeroicArmorPieces.count(victim),
                        ctx.integer("cost"), ctx.integer("cost-period"), ctx.integer("anti-swap"));
            }
        }
    }
}
