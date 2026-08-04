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
 * {@code POTION_AMP_REDUCE} — sap LEVELS off a named potion effect the target already has, for a window,
 * then give it back (Mortal Coil's heart drain). The PARTIAL sibling of {@code POTION_LOCK}: the buff stays
 * on at {@code source − amount}, and every re-application inside the window is held to that same ceiling, so
 * a maintained grant cannot refresh its way out of it.
 *
 * <p>{@code POTION_LOCK} is not a stand-in. It denies the type outright, which is the same thing only while
 * the source sits at or below {@code amount} — and this pack's HEALTH_BOOST grants run far above that line,
 * so a strip would take every bonus heart at the moment its holder is being hit instead of the authored few.
 *
 * <p>Nothing is armed against a target that does not currently carry the effect: the ceiling is measured
 * from the live source, and there is no source to measure. On HEALTH_BOOST the current-health clamp is
 * downward-only and only by what was taken — {@code amount} levels of hearts go, and the restore at expiry
 * brings max health back while leaving current health where it landed.
 */
public final class PotionAmpReduceEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("POTION_AMP_REDUCE")
            .param("effect", D.potionEffect())
            .param("amount", D.INT.min(1).def(1), "levels to sap; at or above the source the effect is denied")
            .param("duration", D.TICKS.def(60))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Reduce the LEVEL of a potion effect the target already has by `amount` for `duration` "
                    + "ticks, then restore it. Re-applications during the window are held to the same "
                    + "reduced ceiling, and a reduction that leaves nothing denies the effect for the "
                    + "window. A target without the effect is untouched. Unlike POTION_LOCK this takes only "
                    + "part of the buff, so a Health Boost VI sapped by 2 keeps four of its six tiers.")
            .example("{ POTION_AMP_REDUCE: { effect: HEALTH_BOOST, amount: 2, duration: 48, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        int amount = ctx.integer("amount"); // LEVELS; amplifiers differ by a constant 1, so a difference is the same
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.potionAmpReduce(target, effect, amount, duration);
        }
    }
}
