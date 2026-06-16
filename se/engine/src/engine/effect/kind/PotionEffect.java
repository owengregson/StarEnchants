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
 * {@code POTION} — apply a potion effect to the target(s) for a duration in ticks
 * (docs/architecture.md §7). Stateless; emits one {@code potion} intent per resolved
 * target and never touches an entity directly. The {@code effect} arg is a
 * version-volatile handle: it is authored as a name (e.g. {@code STRENGTH}) and
 * resolved to an interned id at compile time, so the runtime never sees a renamed
 * constant (§9). {@link Affinity#TARGET_ENTITY}: the {@code Sink} routes each intent
 * to the owning entity's thread.
 */
public final class PotionEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("POTION")
            .param("effect", D.potionEffect())
            .param("amplifier", D.INT.min(0))
            .param("duration", D.TICKS)
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Apply a potion effect to the target(s). The effect name is resolved to a handle at compile time.")
            .example("POTION:STRENGTH:1:100")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        int amplifier = ctx.integer("amplifier");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.potion(target, effect, amplifier, duration);
        }
    }
}
