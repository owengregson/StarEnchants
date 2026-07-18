package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code SWAP_POSITION} — the Castling reforge marker (ADR-0071): declares the channel the reforge
 * service runs — target the enemy in your crosshair (range, line of sight), channel for
 * channel-ticks with audible countdown ticks and warnings BOTH sides, then swap positions
 * (velocities zeroed, each keeps their own facing). Breaking line of sight, range, world, or either
 * party dying aborts the channel; the cooldown stays spent either way (the countdown IS the
 * enemy's counterplay — an abort that refunded would make answering it free for the caster).
 * Service-owned (the DIG_HOME rule): {@link #run} emits nothing.
 */
public final class SwapPositionEffect implements EffectKind {

    public static final String HEAD = "SWAP_POSITION";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("range", D.DOUBLE.min(1).def(12), "crosshair target acquisition + max channel distance")
            .param("channel", D.TICKS.def(40), "channel length (the 2-second countdown)")
            .param("check-period", D.INT.range(1, 10).def(2), "LOS/validity re-check cadence, ticks")
            .param("cue-period", D.TICKS.def(10), "countdown tick-cue cadence")
            .param("range-slack", D.DOUBLE.min(0).def(2), "extra blocks past range before a drift aborts")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Castling (reforges): lock the enemy in your crosshair (range, line of sight), channel for "
                    + "channel ticks with audible countdown cues and a warning to the victim, then both of you "
                    + "swap positions — velocities zeroed, each keeps their own facing. Line of sight broken, "
                    + "range + slack exceeded, a world change or a death aborts it; the use stays spent. "
                    + "Reforge-service-owned: this effect emits no intent of its own.")
            .example("{ SWAP_POSITION: { range: 12, channel: 40 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Service-owned marker (the DIG_HOME rule): CastlingService starts the channel from this effect's
        // compiled args at the reforge activation success point.
    }
}
