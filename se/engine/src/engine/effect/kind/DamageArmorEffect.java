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
 * {@code DAMAGE_ARMOR} — wear down the durability of the target(s) worn armor
 * (docs/architecture.md §7). Stateless; emits a {@code damageArmor} intent per resolved
 * target and never touches an item or entity directly. {@link Affinity#TARGET_ENTITY}:
 * the damage lands on the target's equipment, so on Folia the {@code Sink} routes each
 * intent to the target's region thread — declaring it here is all an author does.
 */
public final class DamageArmorEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE_ARMOR")
            .param("amount", D.INT.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Damage the durability of the target's worn armor.")
            .example("DAMAGE_ARMOR:50")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        for (LivingEntity target : ctx.targets("who")) {
            sink.damageArmor(target, amount);
        }
    }
}
