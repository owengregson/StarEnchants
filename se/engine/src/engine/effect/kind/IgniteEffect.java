package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code IGNITE} — set the target(s) on fire for a duration in ticks
 * (docs/architecture.md §7). Stateless; emits an {@code ignite} intent per resolved
 * target and never touches an entity directly. {@link Affinity#TARGET_ENTITY}: the
 * {@code Sink} routes each intent to the owning entity's thread.
 */
public final class IgniteEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IGNITE")
            .param("duration", D.TICKS)
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Set the target(s) on fire for a duration in ticks.")
            .example("IGNITE:60")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.ignite(target, duration);
        }
    }
}
