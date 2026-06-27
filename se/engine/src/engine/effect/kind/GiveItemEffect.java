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

/** {@code GIVE_ITEM} — give a material to the player target(s) (§C); {@code material} interned at compile (§9). */
public final class GiveItemEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GIVE_ITEM")
            .param("material", D.material())
            .param("count", D.INT.min(1).def(1))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Give a material to the player target(s); overflow drops at their feet.")
            .example("{ GIVE_ITEM: { material: DIAMOND, count: 1, who: \"@Self\" } }")
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
                sink.giveItem(p, material, count);
            }
        }
    }
}
