package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code FILL_OXYGEN} — refill the target(s) air supply (docs/architecture.md §7).
 * No params; stateless; emits a {@code fillAir} intent per resolved target and never
 * touches an entity directly. {@link Affinity#TARGET_ENTITY}: restoring air mutates
 * the target, so the {@code Sink} routes each intent to the owning entity's thread.
 */
public final class FillOxygenEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FILL_OXYGEN")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Refill the target's air supply.")
            .example("FILL_OXYGEN")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.fillAir(target);
        }
    }
}
