package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code GRAPPLE} — the Leviathan's Reach reforge marker (ADR-0071): a fishing-rod-style raycast
 * along the caster's facing. An enemy in sight closer than the first wall → reel THEM to
 * reel-distance blocks in front of the caster (velocity zeroed, their facing kept) plus a brief
 * Slowness — no damage. Terrain first → zip the CASTER to it with a computed velocity arc. A real
 * FishHook is not spawnable anywhere in the 1.8→26.x range (CraftRegionAccessor has no branch for
 * it), so the hook is a dust line and the physics are computed — the "rod" is the look, not the
 * entity. Service-owned (the DIG_HOME rule) because the runtime resolves ONE selector per effect
 * and this mechanism needs TWO rays: GrappleService resolves entity + block via the era raycast at
 * the reforge activation success point, picks the closer, and emits the {@code grapple} sink
 * intent — {@link #run} emits nothing. The closer-of-the-two rule also compensates the 1.8
 * dot-scan's wall-blindness.
 */
public final class GrappleEffect implements EffectKind {

    public static final String HEAD = "GRAPPLE";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("range", D.DOUBLE.min(1).def(14), "hook range in blocks (both rays)")
            .param("hook-speed", D.DOUBLE.min(0.5).def(2.0), "hook flight speed, blocks per tick (cosmetic delay)")
            .param("reel-distance", D.DOUBLE.min(0.5).def(2.0), "where a hooked enemy lands, blocks in front of you")
            .param("slow-effect", D.potionEffect().def("SLOW"), "the brief debuff a reeled enemy gets")
            .param("slow-level", D.INT.range(1, 10).def(2), "its amplifier + 1 (authoring convention)")
            .param("slow-duration", D.TICKS.def(60), "its length")
            .param("zip-strength", D.DOUBLE.min(0).def(0.34), "terrain mode: velocity per block of distance (capped)")
            .param("zip-cap", D.DOUBLE.min(0).def(3.2), "terrain mode: velocity magnitude cap")
            .param("zip-rise", D.DOUBLE.min(0).def(0.25), "terrain mode: extra upward boost")
            .param("particle", D.particle().def("REDSTONE"))
            .param("r", D.INT.range(0, 255).def(200))
            .param("g", D.INT.range(0, 255).def(220))
            .param("b", D.INT.range(0, 255).def(255))
            .param("size", D.DOUBLE.min(0).def(1))
            .param("density", D.DOUBLE.min(0).def(3), "line motes per block")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Leviathan's Reach (reforges): throw a grappling line along your facing (range blocks). An "
                    + "enemy in sight closer than the first block is reeled to reel-distance blocks in front of "
                    + "you with a brief slow and no damage; otherwise you zip to the terrain point. Open air "
                    + "wastes the throw. Reforge-service-owned: this effect emits no intent of its own.")
            .example("{ GRAPPLE: { range: 14, hook-speed: 2.0, reel-distance: 2.0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Service-owned marker (the DIG_HOME rule): GrappleService resolves both rays and emits the
        // grapple intent from this effect's compiled args at the reforge activation success point.
    }
}
