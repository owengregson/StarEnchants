package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code MARK_ZONE} — lay a wearer-owned area zone centred on each target: a cylinder of {@code radius} blocks
 * active for {@code duration} (devil's Hell's Kitchen — the hellfire floor under a struck enemy). The zone is
 * owned by the ACTOR, and the {@code %victim.inzone%} fact reads it, so a separate ATTACK bonus can deal more to
 * an enemy standing in it. An inline per-owner registry write (no entity hop), like {@code MARK} — the centre is
 * each target's current location, read on the firing thread.
 */
public final class MarkZoneEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MARK_ZONE")
            .param("radius", D.DOUBLE.min(0).def(4))
            .param("duration", D.TICKS.def(100))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Lay an actor-owned cylinder of `radius` blocks under each target for `duration` ticks. Read by "
                    + "the %victim.inzone% fact, so a condition-gated bonus can deal more to an enemy inside it.")
            .example("{ MARK_ZONE: { radius: 4, duration: 100, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        if (owner == null) {
            return;
        }
        double radius = ctx.dbl("radius");
        int duration = ctx.integer("duration");
        for (LivingEntity who : ctx.targets("who")) {
            Location center = who.getLocation(); // firing-thread read; the victim is region-local on an ATTACK
            sink.markZone(center, owner, radius, duration);
        }
    }
}
