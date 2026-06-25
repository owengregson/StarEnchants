package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code DROP_ITEM} — drop a material as an item entity at the activation location (docs/v3-directives.md §C).
 * {@code material} is a handle arg interned at compile time (§9). No-op when there is no activation
 * location. {@link Affinity#REGION}: the drop routes to the region thread owning the location.
 */
public final class DropItemEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DROP_ITEM")
            .param("material", D.material())
            .param("count", D.INT.min(1).def(1))
            .affinity(Affinity.REGION)
            .doc("Drop a material as an item at the activation location. No-op if there is no location.")
            .example("DROP_ITEM:DIAMOND:1")
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
