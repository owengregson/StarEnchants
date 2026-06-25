package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code TELEPORT} — teleport the target(s) to the actor or the victim (docs/architecture.md §7; v3.3 §C).
 * The {@code to} argument picks the destination party: {@code VICTIM} (default — e.g. {@code TELEPORT:VICTIM}
 * blinks the attacker to the entity they hit) or {@code ACTOR} (e.g. {@code TELEPORT:ACTOR:@Victim} pulls the
 * victim to the attacker). A no-op when the chosen destination party is absent (a non-combat activation).
 *
 * <p>{@link Affinity#TARGET_ENTITY}: routed to the target's region thread ({@code teleportAsync}). The
 * destination is read from firing-thread-safe context actors and cloned by the Sink, so a deferred (WAIT)
 * hop still lands on an owned snapshot.
 */
public final class TeleportEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TELEPORT")
            .param("to", D.enumOf("VICTIM", "ACTOR").def("VICTIM"), "destination party: the victim or the actor")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Teleport the target to the actor's or the victim's location.")
            .example("TELEPORT:VICTIM")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location destination = "ACTOR".equals(ctx.str("to"))
                ? (ctx.actor() == null ? null : ctx.actor().getLocation())
                : (ctx.victim() == null ? null : ctx.victim().getLocation());
        if (destination == null) {
            return; // the destination party is absent for this activation — nothing to do
        }
        for (LivingEntity target : ctx.targets("who")) {
            sink.teleport(target, destination);
        }
    }
}
