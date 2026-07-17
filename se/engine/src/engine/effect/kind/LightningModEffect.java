package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code LIGHTNING_MOD} — a worn PASSIVE channel (ADR-0063): the summed {@code amount} percent of a
 * player's worn abilities scales the authored damage payload of the wearer's LIGHTNING procs, read
 * suppression-aware at bolt emit (the WATER_SPEED / worn-HEALTH rule — channel-owned, so {@link #run}
 * is a no-op). Cosmetic {@code damage: 0} bolts stay cosmetic; the vanilla strike splash is untouched.
 */
public final class LightningModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("LIGHTNING_MOD")
            .param("amount", D.DOUBLE.min(-100))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("While worn (PASSIVE): the wearer's LIGHTNING effects deal amount% more authored damage "
                    + "(summed across worn sources, suppression-aware, read when the bolt fires). "
                    + "Negative values reduce, floored at a cosmetic bolt; the vanilla splash is untouched.")
            .example("{ LIGHTNING_MOD: { amount: 10 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Channel-owned (the WATER_SPEED rule): LightningBoost reads live WornState + suppression at the
        // sink's bolt emit, so an event-path run must not add into a second channel.
    }
}
