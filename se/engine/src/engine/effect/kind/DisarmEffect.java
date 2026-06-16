package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/**
 * {@code DISARM} — make the target(s) drop the item in their main hand
 * (docs/architecture.md §7). Stateless; emits one {@code disarm} intent per resolved
 * target and never touches an entity directly. {@link Affinity#TARGET_ENTITY}: the
 * {@code Sink} routes each intent to the owning entity's thread, where reading its
 * equipment and dropping at its location is region-correct.
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
