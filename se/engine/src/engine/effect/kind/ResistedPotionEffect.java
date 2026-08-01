package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code RESISTED_POTION} — make one shared proc roll, then apply a potion to each resolved target whose
 * worn-enchant resistance does not narrow that same roll out of the proc window.
 */
public final class ResistedPotionEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("RESISTED_POTION")
            .param("chance", D.DOUBLE.min(0).max(100))
            .param("resistance-enchant", D.STRING)
            .param("resistance-enchant-alt", D.STRING.def(""))
            .param("resist-per-level", D.DOUBLE.min(0))
            .param("effect", D.potionEffect())
            .param("level", D.INT.min(1))
            .param("duration", D.TICKS)
            .param("blocked-message", D.STRING.def(""))
            .param("cue-block", D.enumOf("none", "current-or-stone").def("none"))
            .param("cue-y-offset", D.DOUBLE.def(0))
            .target("who", T.AOE)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Use one shared base-chance roll for all targets, subtracting each target's worn resistance"
                    + " enchant level independently before applying the potion.")
            .example("{ RESISTED_POTION: { chance: 12, resistance-enchant: enchants/metaphysical,"
                    + " resist-per-level: 4, effect: SLOW, level: 2, duration: 60,"
                    + " who: '@Aoe{r=2,filter=NONALLIES}' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double chance = ctx.dbl("chance");
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= chance) {
            return;
        }
        boolean currentCue = "current-or-stone".equalsIgnoreCase(ctx.str("cue-block"));
        for (LivingEntity target : ctx.targets("who")) {
            sink.resistedPotion(target, roll, chance, ctx.str("resistance-enchant"),
                    ctx.str("resistance-enchant-alt"), ctx.dbl("resist-per-level"),
                    ctx.integer("effect"), ctx.integer("level") - 1,
                    ctx.integer("duration"), ctx.str("blocked-message"), currentCue,
                    ctx.dbl("cue-y-offset"));
        }
    }
}
