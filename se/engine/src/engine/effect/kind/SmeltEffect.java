package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code SMELT} — auto-smelt the block broken by the triggering MINE. An inline read-back like
 * {@code IGNORE_ARMOR}: sets a flag the MINE dispatcher reads after the gate walk to swap the raw drop.
 */
public final class SmeltEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SMELT")
            .param("amount", D.INT.min(1).def(1))
            .param("profile", D.enumOf("RECIPES", "IRON_GOLD_ORE").def("RECIPES"))
            .param("unless-held", D.STRING.def(""))
            .param("unless-held2", D.STRING.def(""))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Auto-smelt the block broken by this MINE activation, with an output amount, recipe profile, "
                    + "and up to two held-enchant exclusions.")
            .example("{ SMELT: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String unless = ctx.args().has("unless-held") ? ctx.str("unless-held") : "";
        String unless2 = ctx.args().has("unless-held2") ? ctx.str("unless-held2") : "";
        if (ctx.actor() != null && ((!unless.isEmpty() && HeldEnchantLevels.held(ctx.actor(), unless) > 0)
                || (!unless2.isEmpty() && HeldEnchantLevels.held(ctx.actor(), unless2) > 0))) {
            return;
        }
        if (!ctx.args().has("amount") && !ctx.args().has("profile")) {
            sink.smelt();
            return;
        }
        int amount = ctx.args().has("amount") ? ctx.integer("amount") : 1;
        boolean ironGoldOnly = ctx.args().has("profile")
                && "IRON_GOLD_ORE".equalsIgnoreCase(ctx.str("profile"));
        sink.smelt(amount, ironGoldOnly);
    }
}
