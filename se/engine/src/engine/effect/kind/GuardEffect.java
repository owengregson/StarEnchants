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
 * {@code GUARD} — spawn guardian mob(s) that target the attacker (docs/v3-directives.md §C combat-flags).
 * A targeted superset of {@code SPAWN_ENTITY}: the mobs spawn at the activation location (the defender on
 * a DEFENSE proc) and each is set to attack the resolved {@code who} target — the attacker — so it is a
 * "summon a defender" retaliation rather than a bare spawn.
 *
 * <ul>
 *   <li>{@code type} — the guardian entity type (resolved cross-version at compile time);</li>
 *   <li>{@code count} — how many to summon (default 1);</li>
 *   <li>{@code ttl} — ticks until each guard is auto-removed (default 200; 0 = permanent);</li>
 *   <li>{@code name} — an optional custom name shown above each guard.</li>
 * </ul>
 *
 * <p>Targets {@link T#ATTACKER}: with no attacker (a non-combat trigger) it spawns nothing — an untargeted
 * spawn is {@code SPAWN_ENTITY}'s job. {@link Affinity#REGION}: routed to the spawn location's region; the
 * mob's target is set on its own spawn thread by the {@link Sink}.
 */
public final class GuardEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GUARD")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(200))
            .param("name", D.STRING.def(""))
            .target("who", T.ATTACKER)
            .affinity(Affinity.REGION)
            .doc("Summon count guardian mobs of type at the activation location, each targeting the "
                    + "attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom "
                    + "name. A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.")
            .example("GUARD:IRON_GOLEM:1:200:&bGuardian")
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
        for (LivingEntity attacker : ctx.targets("who")) {
            sink.guard(attacker, ctx.location(), type, count, ttl, name);
        }
    }
}
