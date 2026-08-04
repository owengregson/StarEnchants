package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import schema.spec.D;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code SOUL_TRANSFER} — steal souls out of the target's gems and credit a fraction to the actor's.
 * {@code REMOVE_SOULS} is the near-miss: it only debits, it is gated on the target being in soul mode, and
 * nothing it takes ever arrives anywhere. A steal is a transfer with a loss term, so both ends are one verb —
 * split across two effects the halves would drift and a cancelled second half would silently vaporise the take.
 *
 * <p>{@code ratio} below 1 is the design, not a rounding artifact: the destroyed remainder is what stops a
 * steal proc from being a soul printing press between two consenting players. {@code 0} is the far end of the
 * same scale — a pure drain that banks nothing, which is Soul Trap's authored value.
 */
public final class SoulTransferEffect implements EffectKind {

    private static final String OVERFLOW_MINT = "mint";

    static final EffectSpec SPEC = EffectSpec.of("SOUL_TRANSFER")
            .param("cap", D.INT.min(1), "the most souls one activation may take")
            .param("ratio", D.DOUBLE.range(0, 1).def(1.0), "fraction of the take the actor keeps; the rest is destroyed")
            .param("overflow", D.enumOf(OVERFLOW_MINT, "discard").def(OVERFLOW_MINT),
                    "what happens when the actor carries no gem to credit")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Move min(target's souls, cap) souls out of the target's gems and credit the actor "
                    + "floor(ratio x stolen) — the remainder is destroyed, not banked. Unlike REMOVE_SOULS this "
                    + "does not require either party to be in soul mode: it reads the gems themselves. "
                    + "overflow=mint gives the actor a fresh gem carrying the credit when they carry none; "
                    + "overflow=discard loses it. A target with no souls is a silent no-op, so the authored "
                    + "condition decides what a dry victim costs.")
            .example("{ SOUL_TRANSFER: { cap: 50, ratio: 0.5, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        int cap = ctx.integer("cap");
        double ratio = ctx.dbl("ratio");
        boolean mint = OVERFLOW_MINT.equals(ctx.str("overflow"));
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player victim && !victim.equals(actor)) {
                sink.transferSouls(actor, victim, cap, ratio, mint);
            }
        }
    }
}
