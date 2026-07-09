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
 * {@code MODIFY_EXP} — canonical experience primitive (§C). Transfer's counterpart is fixed to the activator,
 * not a second selector (an effect resolves one selector; mirrors {@link MoneyEffect}).
 */
public final class ExpEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_EXP")
            .param("amount", D.INT.min(0))
            .param("mode", D.enumOf("give", "take", "transfer").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's experience: give to them, take from them, or transfer (move at most "
                    + "the target's experience to the activator — never more than they hold). Replaces GIVE_EXP.")
            .example("{ MODIFY_EXP: { amount: 50, mode: give, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        String mode = ctx.str("mode");
        boolean transfer = "transfer".equalsIgnoreCase(mode);
        boolean take = "take".equalsIgnoreCase(mode);
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                if (transfer && ctx.actor() != null) {
                    // Each victim is clamped to their own XP, so the activator gains only what was withdrawn
                    // (never the full amount off a victim holding less).
                    sink.transferExp(p, ctx.actor(), amount);
                } else if (transfer || take) {
                    sink.takeExp(p, amount); // plain take, or transfer with no activator to credit
                } else {
                    sink.giveExp(p, amount);
                }
            }
        }
    }
}
