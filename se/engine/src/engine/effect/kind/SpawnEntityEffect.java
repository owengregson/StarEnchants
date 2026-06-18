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
 * {@code SPAWN_ENTITY} — the canonical entity-spawn primitive (docs/v3-directives.md §C), collapsing
 * {@code SPAWN} (a mob at the activation location) and {@code TNT} (primed TNT at the target) into one
 * parameterized kind:
 *
 * <ul>
 *   <li>{@code type} — the entity type (e.g. {@code ZOMBIE}, {@code PRIMED_TNT}); resolved cross-version
 *       at compile time, so {@code PRIMED_TNT}/{@code TNT} both work on every server;</li>
 *   <li>{@code count} — how many to spawn (default 1);</li>
 *   <li>{@code ttl} — ticks until each spawned entity is auto-removed (0 = permanent);</li>
 *   <li>{@code health} — starting/max health for living spawns (0 = leave the type default).</li>
 * </ul>
 *
 * <p>Spawns at each resolved target's location (so {@code SPAWN_ENTITY:...:@Victim} reproduces TNT at
 * the victim); with no resolvable target it falls back to the activation location (the old {@code SPAWN}
 * behavior). A primed-TNT spawn auto-primes with the vanilla fuse, matching the old {@code TNT} kind.
 * {@link Affinity#REGION}: spawning is a world mutation at a location, routed to that location's region.
 */
public final class SpawnEntityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWN_ENTITY")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(0))
            .param("health", D.DOUBLE.min(0).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Spawn count entities of type at the target's (or activation) location; ttl ticks until "
                    + "removal (0 = permanent), optional starting health. Replaces SPAWN/TNT.")
            .example("SPAWN_ENTITY:ZOMBIE:3:0:20")
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
        boolean any = false;
        for (LivingEntity who : ctx.targets("who")) {
            sink.spawnEntity(who.getLocation(), type, count, ttl, health);
            any = true;
        }
        if (!any && ctx.location() != null) {
            sink.spawnEntity(ctx.location(), type, count, ttl, health);
        }
    }
}
