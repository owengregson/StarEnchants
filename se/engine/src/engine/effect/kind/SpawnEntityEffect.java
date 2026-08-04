package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.selector.kind.Targets;
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
            .param("speed", D.DOUBLE.min(0).def(0))
            .param("name", D.STRING.def(""), "custom name shown above each summon; {OWNER} fills in the summoner")
            .param("effects", D.potionEffects().def(""), "potion effects held for the summon's whole life")
            .param("payload-phase", D.enumOf("none", "detonate", "death", "periodic").def("none"),
                    "when the summon runs its owner's SUMMON_PAYLOAD abilities")
            .param("payload-period", D.TICKS.def(40), "ticks between payload pulses (periodic phase only)")
            .param("payload-radius", D.DOUBLE.min(0).def(4), "XZ half-extent of the payload's target box")
            .param("payload-height", D.DOUBLE.min(0).def(0), "Y half-extent; 0 reuses payload-radius")
            .param("payload-filter", D.enumSetOf(Targets.names()).def("ALL"),
                    "which entities the payload targets; A+B keeps only what both admit")
            .param("payload-max-targets", D.INT.min(0).def(0), "nearest-first cap on payload targets (0 = all)")
            .param("scatter", D.INT.range(0, 8).def(0),
                    "spread each summon over a random ±N XZ offset, air-scanned (0 = the exact point)")
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
                    + "and knockback); speed is a multiplier on the spawned entity's vanilla movement-speed "
                    + "base (0 = untouched); name is shown above each summon and effects is a "
                    + "comma-separated potion loadout held for its whole life, each entry optionally "
                    + "levelled with NAME*LEVEL (SPEED*3) — the same styling GUARD takes, so the choice "
                    + "between the two is only about targeting. payload-phase attaches the owner's "
                    + "SUMMON_PAYLOAD abilities to a point in the summon's life: detonate REPLACES the "
                    + "vanilla explosion (no terrain damage, no vanilla entity damage), death fires as it "
                    + "dies, and periodic pulses every payload-period ticks. The payload runs once per "
                    + "entity in a payload-radius x payload-height box around the summon (height 0 reuses "
                    + "the radius), filtered by payload-filter and capped nearest-first by "
                    + "payload-max-targets; a payload needs owner=activator, since the owner is who runs "
                    + "it. scatter spreads the summons over a random offset, air-scanned so none spawns "
                    + "inside terrain. Replaces SPAWN/TNT.")
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
                ctx.bool("invincible"),
                ctx.dbl("speed"),
                ctx.str("name"),
                ctx.ids("effects"),
                ctx.str("payload-phase"),
                ctx.integer("payload-period"),
                ctx.dbl("payload-radius"),
                ctx.dbl("payload-height"),
                ctx.str("payload-filter"),
                ctx.integer("payload-max-targets"),
                ctx.integer("scatter"));
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
