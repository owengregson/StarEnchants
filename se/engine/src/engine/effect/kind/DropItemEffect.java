package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/** {@code DROP_ITEM} — drop a material as an item entity at the activation location; {@code material} interned at compile (§9). */
public final class DropItemEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DROP_ITEM")
            .param("material", D.material())
            .param("count", D.INT.min(1).def(1))
            .affinity(Affinity.REGION)
            .doc("Drop a material as an item at the activation location. No-op if there is no location.")
            .example("{ DROP_ITEM: { material: DIAMOND, count: 1 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            sink.dropItem(loc, ctx.integer("material"), ctx.integer("count"));
        }
    }
}
