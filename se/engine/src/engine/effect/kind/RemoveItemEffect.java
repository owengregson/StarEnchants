package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code REMOVE_ITEM} — remove up to {@code count} of a material from the player target(s)' inventory
 * (docs/v3-directives.md §C). {@code material} is a handle arg interned at compile time (§9).
 * {@link Affinity#TARGET_ENTITY}.
 */
public final class RemoveItemEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_ITEM")
            .param("material", D.material())
            .param("count", D.INT.min(1).def(1))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Remove up to count of a material from the player target(s)' inventory.")
            .example("REMOVE_ITEM:DIAMOND:1:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int material = ctx.integer("material");
        int count = ctx.integer("count");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.removeItem(p, material, count);
            }
        }
    }
}
