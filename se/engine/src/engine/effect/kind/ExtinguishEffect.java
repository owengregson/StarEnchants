package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code EXTINGUISH} — put out the target(s)' fire (docs/architecture.md §7).
 * Stateless; emits an {@code extinguish} intent per resolved target and never touches
 * an entity directly. {@link Affinity#TARGET_ENTITY}: clearing fire ticks mutates the
 * target, so the {@code Sink} routes each intent to the owning entity's thread.
 */
public final class ExtinguishEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXTINGUISH")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Put out the target's fire.")
            .example("EXTINGUISH")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.extinguish(target);
        }
    }
}
