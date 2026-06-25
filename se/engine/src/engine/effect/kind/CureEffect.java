package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code CURE} — clear every active potion effect from the target(s) (docs/architecture.md §7); a full
 * cleanse, broad counterpart of single-effect {@code REMOVE_POTION}. {@link Affinity#TARGET_ENTITY}.
 */
public final class CureEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("CURE")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Clear every active potion effect from the target(s).")
            .example("CURE")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.cure(target);
        }
    }
}
