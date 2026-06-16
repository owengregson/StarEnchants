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
 * {@code TNT} — spawn primed TNT at the target(s) (docs/architecture.md §7).
 * Stateless; emits a {@code spawnTnt} intent per resolved target and never touches a
 * world directly. {@link Affinity#REGION}: spawning entities is a world mutation, so
 * the {@code Sink} routes each intent to the region thread that owns the target's
 * location.
 */
public final class SpawnTntEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TNT")
            .param("count", D.INT.min(1).def(1))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Spawn primed TNT at the target.")
            .example("TNT:1")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int count = ctx.integer("count");
        for (LivingEntity target : ctx.targets("who")) {
            sink.spawnTnt(target.getLocation(), count);
        }
    }
}
