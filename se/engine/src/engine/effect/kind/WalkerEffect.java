package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code WALKER} — lay a temporary frost-walker/bridge platform beneath the target, reverting after {@code ticks}
 * (§C). The revert is best-effort (no temp-block ledger): re-firing over a still-active platform can capture an
 * already-placed tile as "prior" and leave it permanent — adequate for a transient walk-assist.
 */
public final class WalkerEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("WALKER")
            .param("material", D.material())
            .param("ticks", D.TICKS.def(60))
            .param("radius", D.INT.range(0, 4).def(1))
            .param("replace", D.enumOf("AIR_ONLY", "REPLACEABLE", "ANY").def("REPLACEABLE"))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .actorOrigin()
            .doc("Lay a temporary platform of a material under the target for a duration (then revert), "
                    + "out to a radius. replace = AIR_ONLY | REPLACEABLE (air/liquid) | ANY.")
            .example("{ WALKER: { material: ICE, ticks: 80, radius: 1 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int material = ctx.integer("material");
        int ticks = ctx.integer("ticks");
        int radius = ctx.integer("radius");
        int mode = switch (ctx.str("replace").toUpperCase(java.util.Locale.ROOT)) {
            case "AIR_ONLY" -> 0;
            case "ANY" -> 2;
            default -> 1; // REPLACEABLE
        };
        Location origin = ctx.actorOrigin(); // hoisted: fresh instance per call (ADR-0043)
        Player actor = ctx.actor();
        for (LivingEntity who : ctx.targets("who")) {
            Location base;
            if (who == actor) {
                base = origin; // platform anchors where the actor stood at activation; null → fail closed, skip
            } else {
                try {
                    base = who.getLocation();
                } catch (RuntimeException unreadable) {
                    Regions.swallowed("WalkerEffect.target", unreadable);
                    continue;
                }
            }
            if (base == null) {
                continue;
            }
            sink.tempPlatform(base, material, radius, ticks, mode);
        }
    }
}
