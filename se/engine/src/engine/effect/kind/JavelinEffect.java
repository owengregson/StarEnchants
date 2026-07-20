package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code JAVELIN} — the Javelin reforge marker (ADR-0071): a straight particle javelin along the
 * caster's FULL facing (pitch included) at speed blocks/tick, max-travel blocks total. First living
 * thing on the path: one weapon-swing's damage, knockback × the javelin's own flight direction
 * (straight back along its angle), a lock-ticks CONTROL lock (the view is snapped back on drift,
 * walk is neutralised by Slowness and jump by a negative Jump Boost — but position is never pinned;
 * the victim is driven down a tracked ballistic arc that survives camera-spam), then Nausea. A wall
 * stops it; a miss is wasted. Service-owned (the DIG_HOME rule).
 */
public final class JavelinEffect implements EffectKind {

    public static final String HEAD = "JAVELIN";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("speed", D.DOUBLE.min(0.05).def(0.15), "flight speed, blocks per tick (0.15 = 3 blocks/s)")
            .param("max-travel", D.DOUBLE.min(1).def(12), "max flight distance in blocks")
            .param("hit-radius", D.DOUBLE.min(0.1).def(0.9), "hit detection radius around the tip")
            .param("damage-mode", D.enumOf("WEAPON", "FLAT").def("WEAPON"),
                    "WEAPON = one swing of the held weapon (era-read); FLAT = the damage param")
            .param("damage", D.DOUBLE.min(0).def(7.0), "FLAT mode damage, RAW health-space")
            .param("knockback", D.DOUBLE.min(0).def(1.3), "knockback multiplier along the flight angle")
            .param("knockback-base", D.DOUBLE.min(0).def(0.45), "base knockback velocity one multiplier buys")
            .param("lock", D.TICKS.def(20), "control-lock length: view snapped, walk+jump locked, driven down a tracked arc")
            .param("lock-delay", D.TICKS.def(5), "ticks after impact before the lock arms (lets the knock land first)")
            .param("nausea-effect", D.potionEffect().def("CONFUSION"), "the post-lock debuff")
            .param("nausea-duration", D.TICKS.def(100), "its length (100 = 5 s)")
            .param("particle", D.particle().def("REDSTONE"))
            .param("r", D.INT.range(0, 255).def(120))
            .param("g", D.INT.range(0, 255).def(200))
            .param("b", D.INT.range(0, 255).def(255))
            .param("size", D.DOUBLE.min(0).def(1.2))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Javelin (reforges): a straight particle javelin at speed blocks/tick along the FULL facing "
                    + "(pitch included), max-travel blocks. On the first living hit: one weapon-swing's damage "
                    + "(or FLAT damage), knockback × along the flight angle, a lock-tick control lock (view "
                    + "snapped back + walk/jump locked; driven down a tracked arc), then nausea. Speed "
                    + "is authored (sidestep to dodge); a miss is wasted. Reforge-service-owned: this effect "
                    + "emits no intent of its own.")
            .example("{ JAVELIN: { speed: 0.15, max-travel: 12, knockback: 1.3, lock: 20 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Service-owned marker (the DIG_HOME rule): JavelinService launches the flight from this
        // effect's compiled args at the reforge activation success point.
    }
}
