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
 * {@code VELOCITY} — canonical movement primitive (§C): {@code add} an x/y/z vector, or shove {@code away}
 * from / drag {@code toward} an anchor point.
 *
 * <p>The anchor used to be the activator alone, which cannot express a defensive self-launch: the wearer IS the
 * activator there, so "away from the activator" is a shove away from nothing. {@code anchor} names the point
 * instead — the activator, the attacker that just hit them, or the combat victim — and {@code mode: toward}
 * reverses the sign for harpoon-style pulls, whose magnitude is an ordinary numeric arg and so may be an
 * expression over the activation's facts.
 */
public final class VelocityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("VELOCITY")
            .param("mode", D.enumOf("add", "away", "toward").def("add"))
            .param("x", D.DOUBLE.def(0))
            .param("y", D.DOUBLE.def(0))
            .param("z", D.DOUBLE.def(0))
            .param("strength", D.DOUBLE.min(0).def(0))
            .param("anchor", D.enumOf("activator", "attacker", "victim").def("activator"),
                    "the point away/toward is measured from")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .actorOrigin()
            .doc("Apply velocity to the target(s): mode=add uses x/y/z; mode=away shoves them back from the "
                    + "anchor with strength and mode=toward drags them to it. anchor picks the point — the "
                    + "activator (default), the attacker that hit them, or the combat victim — so a defensive "
                    + "proc can launch the wearer away from whoever struck. Replaces THROW/LAUNCH/KNOCKBACK.")
            .example("{ VELOCITY: { mode: add, x: 0, y: 1.2, z: 0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String mode = ctx.str("mode");
        boolean away = "away".equalsIgnoreCase(mode);
        if (away || "toward".equalsIgnoreCase(mode)) {
            Location from = anchor(ctx);
            if (from == null) {
                return; // no anchor resolved (uncapturable actor, or a non-combat activation) — no shove
            }
            // A negative impulse is the same vector reversed, so `toward` needs no second Sink verb.
            double strength = away ? ctx.dbl("strength") : -ctx.dbl("strength");
            for (LivingEntity target : ctx.targets("who")) {
                sink.knockback(target, from, strength);
            }
        } else {
            double x = ctx.dbl("x");
            double y = ctx.dbl("y");
            double z = ctx.dbl("z");
            for (LivingEntity target : ctx.targets("who")) {
                sink.launch(target, x, y, z);
            }
        }
    }

    /**
     * The point the impulse is measured from. The activator anchor is the ADR-0043 origin snapshot; the other two
     * are live combat-party reads, guarded because either handle may be cross-region (a resolved shooter).
     */
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
            return entity.getLocation();
        } catch (RuntimeException unreadable) {
            Regions.swallowed("VelocityEffect.anchor", unreadable);
            return null;
        }
    }
}
