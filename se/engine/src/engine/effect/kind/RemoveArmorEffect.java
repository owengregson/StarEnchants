package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code REMOVE_ARMOR} — strip one random worn armour piece from the target and drop it; the armour
 * counterpart of {@link DisarmEffect}. {@link Affinity#TARGET_ENTITY}: routed to the owner's thread, where
 * reading its equipment and dropping at its location is region-correct.
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
