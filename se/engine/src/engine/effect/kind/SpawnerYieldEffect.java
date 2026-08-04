package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code SPAWNER_YIELD} — a worn PASSIVE channel: while a wearer is in scope of a spawner, each of its spawns
 * rolls to come out more than once. Bunny Mask.
 *
 * <p>Channel-owned, so {@link #run} is a no-op (the {@code LIGHTNING_MOD} / {@code WATER_SPEED} rule): nothing
 * PROCS this. A spawner firing is not a player action the engine routes, and the wearer test has to be asked at
 * the moment of the spawn — a window armed at proc time would go stale the instant they walked away, which is
 * exactly the caching bug the port deliberately drops (deviation D-11-10). {@code SpawnerYieldListener} reads
 * live worn state per spawn instead.
 */
public final class SpawnerYieldEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWNER_YIELD")
            .param("chance", D.DOUBLE.range(0, 100).def(65), "percent of spawns that come out multiplied")
            .param("extra", D.INT.range(1, 8).def(1), "copies added on a winning roll")
            .param("scope", D.enumOf("chunk", "radius").def("chunk"), "where the wearer counts as present")
            .param("radius", D.DOUBLE.min(0).def(16), "blocks, for scope: radius; ignored for scope: chunk")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("While worn (PASSIVE): every spawner spawn near the wearer rolls `chance`% to add `extra` "
                    + "copies of the same mob at the same spot. `scope: chunk` counts a wearer standing in the "
                    + "spawn's own chunk; `scope: radius` counts one within `radius` blocks of it. The wearer "
                    + "test is asked PER SPAWN against live worn state, so walking away stops it immediately. "
                    + "Grants do NOT stack — two wearers at one spawner get the stronger one's yield, not both. "
                    + "The added copies spawn as CUSTOM, so they never re-trigger this and never count against "
                    + "the spawner's own budget.")
            .example("{ SPAWNER_YIELD: { chance: 65, extra: 1, scope: chunk } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Channel-owned (the WATER_SPEED rule): SpawnerYieldListener reads live WornState at each spawn, so an
        // event-path run must not add into a second channel.
    }
}
