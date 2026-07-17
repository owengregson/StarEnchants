package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code WATER_SPEED} — a worn PASSIVE/HELD channel (ADR-0060): the summed {@code efficiency} of a
 * player's worn abilities feeds the ONE plugin-owned {@code water_movement_efficiency} attribute modifier
 * the {@code WaterSpeedDriver} reconciles (the worn-HEALTH rule — driver-owned, so {@link #run} is a
 * no-op). The attribute only acts inside the vanilla water-travel branch, so no in-water condition is
 * needed. Real on 1.21+; a recorded no-op on 1.17.1–1.20.6 and the 1.8.9 lane.
 */
public final class WaterSpeedEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("WATER_SPEED")
            .param("efficiency", D.DOUBLE.range(0, 1))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Underwater movement boost while worn (PASSIVE/HELD): efficiency feeds the vanilla "
                    + "water_movement_efficiency attribute through one reconciled plugin-owned modifier "
                    + "(1.21+ only; older servers and 1.8.9 keep everything else and skip the boost). "
                    + "0.09 ~ +10%, 0.14 ~ +15%, 0.20 ~ +20%, 0.26 ~ +25% swim speed.")
            .example("{ WATER_SPEED: { efficiency: 0.09 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Driver-owned channel (the worn-HEALTH rule): WaterSpeedDriver reconciles the modifier from live
        // WornState + suppression, so an event-path run must not add into a second channel.
    }
}
