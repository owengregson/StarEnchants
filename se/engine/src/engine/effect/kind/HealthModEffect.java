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
 * {@code MODIFY_HEALTH} — canonical current-health primitive (§C); distinct from {@code HEALTH}, which shifts
 * the <em>maximum</em>-health attribute. Transfer's counterpart is fixed to the activator, not a second
 * selector (an effect resolves one selector; mirrors {@link MoneyEffect}).
 */
public final class HealthModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_HEALTH")
            .param("amount", D.DOUBLE.min(0))
            .param("cap", D.DOUBLE.min(0).def(0), "optional maximum resolved amount; 0 = uncapped")
            .param("mode", D.enumOf("give", "take", "transfer", "set").def("give"))
            .param("base-round", D.enumOf("none", "floor").def("none"),
                    "for give mode, floor uses floor(current health) + amount before clamping")
            .param("success-sound", D.sound().optional(),
                    "private player cue emitted only when give mode actually raises health")
            .param("sound-volume", D.DOUBLE.min(0).def(1))
            .param("sound-pitch", D.DOUBLE.min(0).def(1))
            .param("require-headroom", D.BOOL.def(false),
                    "heal only when current + amount does not exceed max")
            .param("strict-headroom", D.BOOL.def(false),
                    "with require-headroom, require current + amount to be strictly below max")
            .param("success-particle", D.particle().optional())
            .param("particle-count", D.INT.min(0).def(1))
            .param("particle-spread", D.DOUBLE.min(0).def(0))
            .param("particle-speed", D.DOUBLE.min(0).def(0))
            .param("particle-anchor", D.enumOf("body", "feet", "eye").def("body"))
            .param("particle-y-offset", D.DOUBLE.min(-16).max(16).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a target's health: give heals them, take deals direct health damage, transfer "
                    + "(lifesteal) damages the target and heals the activator by the same amount, set forces "
                    + "their health to the amount. Replaces HEAL.")
            .example("{ MODIFY_HEALTH: { amount: 4, mode: give, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        double cap = ctx.args().has("cap") ? ctx.dbl("cap") : 0.0;
        if (cap > 0) {
            amount = Math.min(amount, cap);
        }
        String mode = ctx.str("mode");
        if ("set".equalsIgnoreCase(mode)) {
            for (LivingEntity target : ctx.targets("who")) {
                sink.setHealth(target, amount);
            }
            return;
        }
        boolean transfer = "transfer".equalsIgnoreCase(mode);
        boolean take = transfer || "take".equalsIgnoreCase(mode);
        boolean floorBase = ctx.args().has("base-round")
                && "floor".equalsIgnoreCase(ctx.str("base-round"));
        int hit = 0;
        for (LivingEntity target : ctx.targets("who")) {
            if (take) {
                sink.damage(target, amount, ctx.actor()); // attributed / same-hit-folded like DAMAGE (ADR-0054)
                hit++;
            } else if (ctx.args().has("require-headroom") && ctx.bool("require-headroom")) {
                int anchor = switch (ctx.str("particle-anchor").toLowerCase(java.util.Locale.ROOT)) {
                    case "feet" -> 1;
                    case "eye" -> 2;
                    default -> 0;
                };
                sink.healIfHeadroom(target, amount, ctx.bool("strict-headroom"),
                        ctx.args().has("success-particle") ? ctx.integer("success-particle") : -1,
                        ctx.integer("particle-count"), ctx.dbl("particle-spread"),
                        ctx.dbl("particle-speed"), anchor, ctx.dbl("particle-y-offset"));
            } else if (ctx.args().has("success-sound")) {
                sink.healWithPrivateSound(target, amount, floorBase, ctx.integer("success-sound"),
                        (float) ctx.dbl("sound-volume"), (float) ctx.dbl("sound-pitch"));
            } else if (floorBase) {
                sink.healFromFloor(target, amount);
            } else {
                sink.heal(target, amount);
            }
        }
        if (transfer && hit > 0 && ctx.actor() != null) {
            sink.heal(ctx.actor(), amount * hit); // lifesteal: the activator gains what was drained
        }
    }
}
