package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code DESPAWN} — remove target mobs outright: no drops, no XP, no death event. {@code KILL} cannot express
 * this (it fires a real death, so an AoE mob-clear would shower the caster in loot). Players are skipped
 * silently rather than diagnosed: the same {@code @Aoe} that clears mobs routinely also resolves players, and
 * a fault there would fire on a correctly-authored ability.
 */
public final class DespawnEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DESPAWN")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Silently remove the target mob(s) — no drops, no experience, no death event, so nothing "
                    + "downstream (kill counters, other plugins' death hooks) sees a kill. Players are never "
                    + "removed. Pair with @Aoe{filter=MOBS} for an area mob-clear; use KILL when the drops "
                    + "and the death are the point.")
            .example("{ DESPAWN: { who: \"@Aoe{r=8, filter=MOBS}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if (!(target instanceof Player)) {
                sink.despawn(target);
            }
        }
    }
}
