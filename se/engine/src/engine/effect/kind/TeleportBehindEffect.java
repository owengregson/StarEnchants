package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import schema.spec.D;

/**
 * {@code TELEPORT_BEHIND} — blink the {@code who} mover(s) to a spot {@code distance} blocks behind a reference
 * entity ({@code of}: the VICTIM slot — which on a DEFENSE trigger IS the attacker — or the ACTOR), facing the
 * same way the reference faces. The Sink verifies the spot is a safe landing (room + line of sight); on failure
 * {@code onFail: ONTOP} lands on the reference instead, {@code NONE} cancels. Stellar's Dimensional Shift escape;
 * any reposition-behind blink reuses it. The geometry is firing-thread value maths; the safety read is the Sink's.
 */
public final class TeleportBehindEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TELEPORT_BEHIND")
            .param("of", D.enumOf("VICTIM", "ACTOR").def("VICTIM"))
            .param("distance", D.DOUBLE.min(0).def(1))
            .param("onFail", D.enumOf("ONTOP", "NONE").def("ONTOP"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Teleport the mover(s) `distance` blocks behind the reference (of: VICTIM — the attacker on a "
                    + "DEFENSE trigger — or ACTOR), facing as it faces. Unsafe (blocked / wall between) → onFail "
                    + "ONTOP lands on the reference, NONE cancels.")
            .example("{ TELEPORT_BEHIND: { of: VICTIM, distance: 1, onFail: ONTOP, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        LivingEntity reference = "ACTOR".equalsIgnoreCase(ctx.str("of")) ? ctx.actor() : ctx.victim();
        if (reference == null) {
            return;
        }
        Location refLoc = reference.getLocation();
        if (refLoc == null) {
            return;
        }
        // Horizontal unit facing of the reference; default forward if it has no usable look (getDirection null).
        Vector direction = refLoc.getDirection();
        double fx = direction == null ? 0.0 : direction.getX();
        double fz = direction == null ? 1.0 : direction.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1.0e-6) {
            fx = 0.0;
            fz = 1.0;
        } else {
            fx /= len;
            fz /= len;
        }
        double distance = ctx.dbl("distance");
        // Build from components rather than clone(): the spot is behind the reference, facing as it faces.
        Location behind = new Location(refLoc.getWorld(),
                refLoc.getX() - fx * distance, refLoc.getY(), refLoc.getZ() - fz * distance,
                refLoc.getYaw(), 0f);
        Location fallback = "NONE".equalsIgnoreCase(ctx.str("onFail")) ? null
                : new Location(refLoc.getWorld(), refLoc.getX(), refLoc.getY(), refLoc.getZ(),
                        refLoc.getYaw(), refLoc.getPitch());
        Location sightFrom = ctx.actor() != null ? ctx.actor().getEyeLocation() : refLoc;
        for (LivingEntity mover : ctx.targets("who")) {
            sink.teleportSafe(mover, behind, fallback, sightFrom);
        }
    }
}
