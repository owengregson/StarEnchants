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
 * {@code FLY} — grant the player target(s) temporary flight for a duration in ticks
 * (docs/architecture.md §7). Stateless; emits a {@code setFlight} intent per resolved
 * player target. Only players can fly, so non-player targets are silently skipped.
 * {@link Affinity#TARGET_ENTITY}: granting flight mutates the target, so on Folia the
 * {@code Sink} routes each intent to the target's region thread — declaring it here is
 * all an author does.
 */
public final class FlyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FLY")
            .param("ticks", D.TICKS.def(200))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Grant the player temporary flight.")
            .example("FLY:200")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int ticks = ctx.integer("ticks");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.setFlight(p, ticks);
            }
        }
    }
}
