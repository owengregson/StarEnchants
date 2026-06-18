package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code WALKER} — lay a temporary platform of blocks beneath the target (docs/v3-directives.md §C), a
 * frost-walker / bridge-style enchant: it places {@code material} in the block layer under the target,
 * out to {@code radius} blocks each way, and reverts to the captured prior blocks after {@code ticks}.
 *
 * <p>{@code replace} controls what the platform may overwrite: {@code AIR_ONLY}, {@code REPLACEABLE}
 * (air or liquid), or {@code ANY}. The revert is best-effort (no temp-block ledger): re-firing over a
 * still-active platform can capture an already-placed tile as "prior" and leave it permanent — adequate
 * for a transient walk-assist; a durable ledger is a follow-up. Stateless; emits one {@code tempPlatform}
 * intent per resolved target. {@link Affinity#REGION}: block work routes to the location's region thread.
 */
public final class WalkerEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("WALKER")
            .param("material", D.material())
            .param("ticks", D.TICKS.def(60))
            .param("radius", D.INT.range(0, 4).def(1))
            .param("replace", D.enumOf("AIR_ONLY", "REPLACEABLE", "ANY").def("REPLACEABLE"))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Lay a temporary platform of a material under the target for a duration (then revert), "
                    + "out to a radius. replace = AIR_ONLY | REPLACEABLE (air/liquid) | ANY.")
            .example("WALKER:ICE:80:1")
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
        for (LivingEntity who : ctx.targets("who")) {
            sink.tempPlatform(who.getLocation(), material, radius, ticks, mode);
        }
    }
}
