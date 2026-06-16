package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code SPAWN} — spawn one or more entities of a type at the activation location
 * (docs/architecture.md §7). Stateless; emits one {@code spawn} intent per entity and
 * never touches the world directly. The {@code type} is a handle arg authored as a
 * token (e.g. {@code ZOMBIE}) and resolved to an interned entity-type id at compile
 * time, so the runtime reads it with {@link EffectCtx#integer} and never sees a renamed
 * constant (§9). No-op when there is no activation location. {@link Affinity#REGION}:
 * the {@code Sink} routes each spawn to the owning region's thread.
 */
public final class SpawnEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SPAWN")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .affinity(Affinity.REGION)
            .doc("Spawn one or more entities of a type at the activation location. No-op if there is no location.")
            .example("SPAWN:ZOMBIE:3")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            int n = ctx.integer("count");
            int type = ctx.integer("type");
            for (int i = 0; i < n; i++) {
                sink.spawn(loc, type);
            }
        }
    }
}
