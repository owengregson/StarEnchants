package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code SPAWN_SWARM} — summon {@code count} entities evenly spaced on a {@code radius}-block ring around
 * the activator at chest height ({@code rise}), each facing directly outward, keeping VANILLA AI, removed
 * after {@code ttl} ticks (ADR-0060). {@code speed < 1} slows each summon to that fraction of its vanilla
 * AI speed (a per-tick velocity damp — the movement-speed attribute does not steer Bat-style hardcoded AI).
 */
public final class SpawnSwarmEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWN_SWARM")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("radius", D.DOUBLE.min(0).def(0.5))
            .param("rise", D.DOUBLE.min(0).def(1.2))
            .param("ttl", D.TICKS.def(300))
            .param("speed", D.DOUBLE.range(0, 1).def(1))
            .affinity(Affinity.REGION)
            .actorOrigin()
            .doc("Summon count entities of type evenly spaced on a radius-block ring around the activator, "
                    + "raised rise blocks (chest height), each facing directly outward, with VANILLA AI, "
                    + "auto-removed after ttl ticks. speed < 1 slows each to that fraction of its vanilla "
                    + "AI speed via a per-tick velocity damp (Bat-style AI ignores the speed attribute).")
            .example("{ SPAWN_SWARM: { type: BAT, count: 10, radius: 0.5, ttl: 300, speed: 0.5 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location origin = ctx.actorOrigin(); // ADR-0043: the firing-thread snapshot, never actor().getLocation()
        if (origin == null) {
            origin = ctx.location(); // uncapturable actor origin — the SPAWN_ENTITY fallback rule
        }
        if (origin == null) {
            return;
        }
        sink.spawnSwarm(origin, ctx.integer("type"), ctx.integer("count"), ctx.dbl("radius"),
                ctx.dbl("rise"), ctx.integer("ttl"), ctx.dbl("speed"));
    }
}
