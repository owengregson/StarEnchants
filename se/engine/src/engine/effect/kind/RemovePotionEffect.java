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
 * {@code REMOVE_POTION} — clear a potion effect from the target(s)
 * (docs/architecture.md §7). Stateless; emits one {@code removePotion} intent per
 * resolved target and never touches an entity directly. The {@code effect} arg is a
 * version-volatile handle: authored as a token (e.g. {@code POISON}) and resolved to
 * an interned id at compile time, so the runtime never sees a renamed constant (§9).
 * {@link Affinity#TARGET_ENTITY}: the {@code Sink} routes each intent to the owning
 * entity's thread.
 */
public final class RemovePotionEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_POTION")
            .param("effect", D.potionEffect())
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Remove a potion effect from the target(s).")
            .example("REMOVE_POTION:POISON")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        for (LivingEntity target : ctx.targets("who")) {
            sink.removePotion(target, effect);
        }
    }
}
