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
 * {@code TELEPORT} — teleport the target(s) to the actor's or the victim's location (§7, §C); no-op when that
 * destination party is absent. The ACTOR destination is the ADR-0043 origin snapshot; the possibly-remote
 * VICTIM is a Regions-guarded live read; the Sink clones, so a deferred (WAIT) hop still lands on an owned
 * snapshot.
 */
public final class TeleportEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TELEPORT")
            .param("to", D.enumOf("VICTIM", "ACTOR").def("VICTIM"), "destination party: the victim or the actor")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .actorOrigin()
            .doc("Teleport the target to the actor's or the victim's location.")
            .example("{ TELEPORT: { to: VICTIM } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location destination;
        if ("ACTOR".equals(ctx.str("to"))) {
            destination = ctx.actorOrigin(); // ADR-0043 snapshot; null → uncapturable actor
        } else {
            LivingEntity victim = ctx.victim();
            if (victim == null) {
                return;
            }
            try {
                destination = victim.getLocation(); // DEFENSE exposes the possibly-remote attacker here
            } catch (RuntimeException unreadable) {
                Regions.swallowed("TeleportEffect.destination", unreadable);
                return;
            }
        }
        if (destination == null) {
            return; // the destination party is absent for this activation — nothing to do
        }
        for (LivingEntity target : ctx.targets("who")) {
            sink.teleport(target, destination);
        }
    }
}
