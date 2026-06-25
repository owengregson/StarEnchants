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
 * {@code SPAWN_ENTITY} — canonical entity-spawn primitive (docs/v3-directives.md §C):
 *
 * <ul>
 *   <li>{@code type} — entity type, resolved cross-version at compile time, so {@code PRIMED_TNT}/{@code TNT}
 *       both work on every server;</li>
 *   <li>{@code count} — how many to spawn (default 1);</li>
 *   <li>{@code ttl} — ticks until each spawn is auto-removed (0 = permanent);</li>
 *   <li>{@code health} — starting/max health for living spawns (0 = leave the type default);</li>
 *   <li>{@code owner} — {@code activator} tames the spawn (when {@link org.bukkit.entity.Tameable}) to the
 *       activator; default {@code none}.</li>
 * </ul>
 *
 * <p>Spawns at each resolved target's location; with no resolvable target it falls back to the activation
 * location. A primed-TNT spawn auto-primes with the vanilla fuse. {@link Affinity#REGION}.
 */
public final class SpawnEntityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWN_ENTITY")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(0))
            .param("health", D.DOUBLE.min(0).def(0))
            .param("owner", D.enumOf("none", "activator").def("none"))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Spawn count entities of type at the target's (or activation) location; ttl ticks until "
                    + "removal (0 = permanent), optional starting health, and owner=activator to tame an owned "
                    + "summon to the activator. Replaces SPAWN/TNT.")
            .example("SPAWN_ENTITY:WOLF:1:0:0:activator")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int type = ctx.integer("type");
        int count = ctx.integer("count");
        int ttl = ctx.integer("ttl");
        double health = ctx.dbl("health");
        java.util.UUID owner = "activator".equalsIgnoreCase(ctx.str("owner")) && ctx.actor() != null
                ? ctx.actor().getUniqueId() : null;
        boolean any = false;
        for (LivingEntity who : ctx.targets("who")) {
            sink.spawnEntity(who.getLocation(), type, count, ttl, health, owner);
            any = true;
        }
        if (!any && ctx.location() != null) {
            sink.spawnEntity(ctx.location(), type, count, ttl, health, owner);
        }
    }
}
