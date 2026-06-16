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
 * {@code LIGHTNING} — strike the target(s) with lightning, optionally dealing extra
 * damage (docs/architecture.md §7). Stateless; emits one {@code lightningAndDamage}
 * intent per resolved target and never touches an entity directly. A {@code damage}
 * of {@code 0} is purely cosmetic. {@link Affinity#TARGET_ENTITY}: the strike lands
 * on the target entity's thread.
 */
public final class LightningEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("LIGHTNING")
            .param("damage", D.DOUBLE.min(0).def(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Strike the target(s) with lightning, optionally dealing extra damage (0 = cosmetic).")
            .example("LIGHTNING:6")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double damage = ctx.dbl("damage");
        for (LivingEntity target : ctx.targets("who")) {
            sink.lightningAndDamage(target, damage);
        }
    }
}
