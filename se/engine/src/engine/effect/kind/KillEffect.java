package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code KILL} — instantly kill the target(s) (docs/architecture.md §7). Takes no
 * params; stateless, emitting a {@code kill} intent per resolved target and never
 * touching an entity directly. {@link Affinity#TARGET_ENTITY}: killing mutates the
 * target, so the {@code Sink} routes each intent to the owning entity's region thread.
 */
public final class KillEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("KILL")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Instantly kill the target.")
            .example("KILL")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.kill(target);
        }
    }
}
