package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code REMOVE_ARMOR} — strip one random worn armour piece from the target and drop it; armour counterpart of
 * {@link DisarmEffect}. {@link Affinity#TARGET_ENTITY} so the equipment read + drop runs on the owner's thread.
 */
public final class RemoveArmorEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_ARMOR")
            .param("destination", D.enumOf("drop", "inventory").def("drop"))
            .param("victim-message", D.STRING.def(""))
            .param("victim-description", D.STRING.def(""))
            .param("attacker-message", D.STRING.def(""))
            .param("blank-lines", D.INT.min(0).def(0))
            .param("sound", D.sound().optional())
            .param("sound-volume", D.DOUBLE.min(0).def(1))
            .param("sound-pitch", D.DOUBLE.min(0).def(1))
            .param("block", D.material().optional())
            .param("block-height", D.INT.min(0).def(1))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Strip one random worn armour piece from the target(s) and drop it.")
            .example("{ REMOVE_ARMOR: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if ("inventory".equalsIgnoreCase(ctx.str("destination")) && target instanceof Player player) {
                sink.unequipArmor(player, ctx.actor(),
                        ctx.args().has("sound") ? ctx.integer("sound") : -1,
                        (float) ctx.dbl("sound-volume"), (float) ctx.dbl("sound-pitch"),
                        ctx.args().has("block") ? ctx.integer("block") : -1, ctx.integer("block-height"),
                        ctx.str("victim-message"), ctx.str("victim-description"),
                        ctx.str("attacker-message"), ctx.integer("blank-lines"));
            } else {
                sink.removeArmor(target);
            }
        }
    }
}
