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
 * along their facing, landing on the FARTHEST standable cell before the first blocked one; never
 * phases into or through terrain. The ray is YAW-ONLY: a grounded player looks slightly down almost
 * always, and a pitched ray would enter the floor cell within the first sample — every real-play
 * blink would zero out with the cooldown spent. Facing a wall point-blank still blinks zero blocks
 * and spends the attempt (the authored downside: walls stop it — a reposition, not a terrain
 * escape). No target slot: self-only by design.
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
            .param("arrival-sound", D.sound().def("ENTITY_ENDERMAN_TELEPORT"),
                    "played ON the player after the hop lands — a sound at the origin is never heard "
                            + "by someone who just teleported away from it")
            .param("arrival-volume", D.DOUBLE.min(0).def(0.6))
            .param("arrival-pitch", D.DOUBLE.min(0).def(1.8))
            .param("arrival-accent", D.sound().def("BLOCK_AMETHYST_BLOCK_CHIME"), "the shimmer layer over the arrival body")
            .param("accent-volume", D.DOUBLE.min(0).def(0.4))
            .param("accent-pitch", D.DOUBLE.min(0).def(1.6))
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
        Location level = origin.clone();
        level.setPitch(0.0f); // yaw-only: a look-down ray would ground the walk in the floor cell
        Vector direction = level.getDirection();
        sink.blinkForward(actor, origin, direction, ctx.dbl("distance"),
                ctx.integer("particle"), ctx.integer("r"), ctx.integer("g"), ctx.integer("b"),
                (float) ctx.dbl("size"), ctx.integer("count"),
                ctx.integer("arrival-sound"), (float) ctx.dbl("arrival-volume"), (float) ctx.dbl("arrival-pitch"),
                ctx.integer("arrival-accent"), (float) ctx.dbl("accent-volume"), (float) ctx.dbl("accent-pitch"));
    }
}
