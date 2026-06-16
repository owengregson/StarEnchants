package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code CURE} — clear every active potion effect from the target(s)
 * (docs/architecture.md §7) — a full cleanse, the broad counterpart of the
 * single-effect {@code REMOVE_POTION}. Stateless; emits one {@code cure} intent per
 * resolved target and never touches an entity directly.
 * {@link Affinity#TARGET_ENTITY}: the {@code Sink} routes each intent to the owning
 * entity's thread.
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
