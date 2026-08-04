package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code FACING_SET} — aim a target's body at (or away from) an anchor, in place.
 *
 * <p>Nothing else on the surface turns a body: {@code TELEPORT} and {@code TELEPORT_BEHIND} both MOVE one and
 * carry whatever direction they were handed. A disorient proc needs the opposite — the position untouched and
 * only the look changed — so the two cannot be composed into it.
 *
 * <p>The reference is an {@code anchor} enum rather than a second selector slot, matching {@code VELOCITY}:
 * "away" is only meaningful relative to something, and the three combat roles are the somethings an activation
 * actually has. The activator anchor is the ADR-0043 origin snapshot, so the common case reads no live entity.
 */
public final class FacingSetEffect implements EffectKind {

    private static final String MODE_AWAY = "away";

    static final EffectSpec SPEC = EffectSpec.of("FACING_SET")
            .param("mode", D.enumOf("toward", MODE_AWAY).def("toward"),
                    "whether the target ends up looking at the anchor or directly away from it")
            .param("anchor", D.enumOf("activator", "attacker", "victim").def("activator"),
                    "which combat party the direction is measured from")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .actorOrigin()
            .doc("Turn each target to face toward (or away from) the anchor, without moving them. Pitch is set "
                    + "too, so an anchor above or below is genuinely looked at. A target sharing the anchor's "
                    + "exact column keeps its current look — there is no direction to turn to — and an "
                    + "activation whose anchor does not resolve turns nobody.")
            .example("{ FACING_SET: { mode: away, anchor: activator, "
                    + "who: \"@AOE{radius: 8, filter: ENEMIES}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location anchor = anchor(ctx);
        if (anchor == null) {
            return;
        }
        boolean away = MODE_AWAY.equals(ctx.str("mode"));
        for (LivingEntity target : ctx.targets("who")) {
            sink.setFacing(target, anchor, away);
        }
    }

    /** The point the look is measured from — {@code VELOCITY}'s anchor rule, verbatim. */
    private static Location anchor(EffectCtx ctx) {
        String anchor = ctx.str("anchor");
        if ("attacker".equalsIgnoreCase(anchor)) {
            return locationOf(ctx.attacker());
        }
        if ("victim".equalsIgnoreCase(anchor)) {
            return locationOf(ctx.victim());
        }
        return ctx.actorOrigin();
    }

    private static Location locationOf(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return entity.getLocation(); // either handle may be cross-region (a resolved shooter, ADR-0043)
        } catch (RuntimeException unreadable) {
            Regions.swallowed("FacingSetEffect.anchor", unreadable);
            return null;
        }
    }
}
