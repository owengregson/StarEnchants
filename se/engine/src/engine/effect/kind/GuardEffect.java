package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code GUARD} — spawn guardian mob(s) that target the attacker (§C combat-flags): a targeted superset of
 * {@code SPAWN_ENTITY} for retaliation. Targets {@link T#ATTACKER}, so with no attacker (a non-combat
 * trigger) it spawns nothing — an untargeted spawn is {@code SPAWN_ENTITY}'s job.
 *
 * <p>{@code health}, {@code speed} and {@code effects} are the same styling {@code SPAWN_ENTITY} takes, so which
 * of the two an author reaches for is a targeting decision alone and never a "but that one can't do X" one. The
 * loadout is applied for the summon's whole life, which is what makes a scaling guardian a real escalation
 * rather than the same golem with a bigger health bar.
 */
public final class GuardEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GUARD")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(200))
            .param("name", D.STRING.def(""))
            .param("health", D.DOUBLE.min(0).def(0), "starting (and maximum) health; 0 keeps the vanilla one")
            .param("speed", D.DOUBLE.min(0).def(0), "movement-speed multiplier; 0 keeps the vanilla one")
            .param("effects", D.potionEffects().def(""), "potion effects held for the guard's whole life")
            .target("who", T.ATTACKER)
            .affinity(Affinity.REGION)
            .doc("Summon count guardian mobs of type at the activation location, each targeting the "
                    + "attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom "
                    + "name. health sets each guard's starting and maximum health, speed multiplies its "
                    + "vanilla movement speed, and effects is a comma-separated potion loadout held for the "
                    + "guard's whole life (all at level 1). A targeted SPAWN_ENTITY for retaliation — author "
                    + "on DEFENSE.")
            .example("{ GUARD: { type: IRON_GOLEM, count: 1, ttl: 200, name: \"&bGuardian\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        if (ctx.location() == null) {
            return; // nowhere to spawn the guard
        }
        int type = ctx.integer("type");
        int count = ctx.integer("count");
        int ttl = ctx.integer("ttl");
        String name = ctx.str("name");
        double health = ctx.dbl("health");
        double speed = ctx.dbl("speed");
        List<Integer> effects = ctx.ids("effects");
        // The activation actor owns each summoned guard (ADR-0049: a hit on it fires the owner's GUARDIAN_HURT).
        java.util.UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        for (LivingEntity attacker : ctx.targets("who")) {
            sink.guard(attacker, ctx.location(), type, count, ttl, name, owner, health, speed, effects);
        }
    }
}
