package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.sink.SummonFlags;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code SPAWN_ENTITY} — canonical entity-spawn primitive (§C); {@code type} resolved cross-version at compile,
 * so {@code PRIMED_TNT}/{@code TNT} both work on every server. Spawns at each resolved target's location,
 * falling back to the activation location when none resolves.
 */
public final class SpawnEntityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWN_ENTITY")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("ttl", D.TICKS.def(0))
            .param("health", D.DOUBLE.min(0).def(0))
            .param("owner", D.enumOf("none", "activator").def("none"))
            .param("powered", D.BOOL.def(false))
            .param("ai", D.BOOL.def(true))
            .param("targeting", D.BOOL.def(true))
            .param("saddled", D.BOOL.def(false))
            .param("mount", D.enumOf("none", "activator").def("none"))
            .param("detonate", D.enumOf("NONE", "PLAYER_HIT").def("NONE"))
            .param("invincible", D.BOOL.def(false))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .actorOrigin()
            .doc("Spawn count entities of type at the target's (or activation) location; ttl ticks until "
                    + "removal (0 = permanent), optional starting health, and owner=activator to tame an owned "
                    + "summon to the activator. ADR-0052 summon flags: powered charges a creeper; ai=false "
                    + "disables mob AI; targeting=false stops the summon acquiring targets; saddled + "
                    + "mount=activator make a horse-type rideable and seat the activator; detonate=PLAYER_HIT "
                    + "makes a creeper explode ONLY when a player hits it (it never self-detonates); "
                    + "invincible=true zeroes all damage to the summon (it cannot die but still takes hits "
                    + "and knockback). Replaces SPAWN/TNT.")
            .example("{ SPAWN_ENTITY: { type: WOLF, count: 1, ttl: 0, health: 0, owner: activator } }")
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
        Player actor = ctx.actor();
        java.util.UUID owner = "activator".equalsIgnoreCase(ctx.str("owner")) && actor != null
                ? actor.getUniqueId() : null;
        SummonFlags flags = new SummonFlags(
                ctx.bool("powered"),
                !ctx.bool("ai"),
                !ctx.bool("targeting"),
                ctx.bool("saddled"),
                "activator".equalsIgnoreCase(ctx.str("mount")),
                "PLAYER_HIT".equalsIgnoreCase(ctx.str("detonate")),
                ctx.bool("invincible"));
        Location origin = ctx.actorOrigin(); // hoisted: fresh instance per call (ADR-0043)
        boolean any = false;
        for (LivingEntity who : ctx.targets("who")) {
            Location base;
            if (who == actor) {
                base = origin; // null → uncapturable actor origin: fall through to the ctx.location() fallback
            } else {
                try {
                    base = who.getLocation();
                } catch (RuntimeException unreadable) {
                    Regions.swallowed("SpawnEntityEffect.target", unreadable);
                    continue;
                }
            }
            if (base == null) {
                continue;
            }
            spawn(sink, base, type, count, ttl, health, owner, actor, flags);
            any = true;
        }
        if (!any && ctx.location() != null) {
            spawn(sink, ctx.location(), type, count, ttl, health, owner, actor, flags);
        }
    }

    /** Default flags keep the plain spawn intent (byte-stable pre-ADR-0052); any flag routes to the summon. */
    private static void spawn(Sink sink, Location at, int type, int count, int ttl, double health,
                              java.util.UUID owner, Player actor, SummonFlags flags) {
        if (flags.none()) {
            sink.spawnEntity(at, type, count, ttl, health, owner);
        } else {
            sink.spawnSummon(at, type, count, ttl, health, owner, actor, flags);
        }
    }
}
