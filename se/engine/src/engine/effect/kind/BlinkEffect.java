package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import schema.spec.D;

/**
 * {@code BLINK} — the Blink reforge (ADR-0071): teleport the caster up to {@code distance} blocks
 * along their full look direction (pitch included — chorus-fruit feel), landing on the FARTHEST
 * standable cell before the first blocked one; never phases into or through terrain. Facing a wall
 * point-blank blinks zero blocks and the attempt is still spent (the authored downside: walls stop
 * it — a reposition, not a terrain escape). No target slot: self-only by design.
 */
public final class BlinkEffect implements EffectKind {

    public static final String HEAD = "BLINK";

    static final EffectSpec SPEC = EffectSpec.of(HEAD)
            .param("distance", D.DOUBLE.range(1, 16).def(4), "max blink distance in blocks")
            .param("particle", D.particle().def("REDSTONE"))
            .param("r", D.INT.range(0, 255).def(170))
            .param("g", D.INT.range(0, 255).def(60))
            .param("b", D.INT.range(0, 255).def(220))
            .param("size", D.DOUBLE.min(0).def(1))
            .param("count", D.INT.min(0).def(10), "departure/arrival puff motes")
            .affinity(Affinity.TARGET_ENTITY)
            .actorOrigin()
            .doc("Blink (reforges): instantly teleport up to distance blocks along your facing if the path is "
                    + "clear — stops at the last open block, never phases into or through terrain. Walls stop "
                    + "it; the use is spent either way.")
            .example("{ BLINK: { distance: 4 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        Location origin = ctx.actorOrigin();      // ADR-0043 snapshot: x/y/z + yaw/pitch
        if (actor == null || origin == null) {
            return;
        }
        Vector direction = origin.getDirection(); // full 3D look, |v| == 1
        sink.blinkForward(actor, origin, direction, ctx.dbl("distance"),
                ctx.integer("particle"), ctx.integer("r"), ctx.integer("g"), ctx.integer("b"),
                (float) ctx.dbl("size"), ctx.integer("count"));
    }
}
