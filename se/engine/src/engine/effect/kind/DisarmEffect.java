package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code DISARM} — make the target(s) drop their main-hand item (§7). {@link Affinity#TARGET_ENTITY} so the
 * equipment read + drop runs on the owner's thread (region-correct on Folia).
 */
public final class DisarmEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DISARM")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Make the target(s) drop their held (main-hand) item.")
            .example("DISARM")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.disarm(target);
        }
    }
}
