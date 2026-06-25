package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code REMOVE_ARMOR} — strip one random worn armour piece from the target and drop it (the Cosmic Enchants-style
 * {@code REMOVE_ARMOR} effect). The armour counterpart of {@link DisarmEffect}: stateless, emits one
 * {@code removeArmor} intent per resolved target, never touches an entity directly.
 * {@link Affinity#TARGET_ENTITY} — the {@code Sink} routes each intent to the owning entity's thread,
 * where reading its equipment and dropping at its location is region-correct.
 */
public final class RemoveArmorEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_ARMOR")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Strip one random worn armour piece from the target(s) and drop it.")
            .example("REMOVE_ARMOR")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.removeArmor(target);
        }
    }
}
