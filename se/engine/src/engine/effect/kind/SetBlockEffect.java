package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code SET_BLOCK} — set one or more blocks to a material (docs/v3-directives.md §C/§A). {@code material} is
 * a handle arg interned at compile time, read with {@link EffectCtx#integer} (§9). Target slot defaults to
 * {@code @Here} (the activation block) and accepts any block/location selector inline (e.g. {@code @Trench}).
 * {@link Affinity#REGION}: each change routes to the region thread owning its location.
 */
public final class SetBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SET_BLOCK")
            .param("material", D.material())
            .target("at", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Set the target block(s) to a material (default @Here = the activation block).")
            .example("SET_BLOCK:OBSIDIAN")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int material = ctx.integer("material");
        for (Location loc : ctx.targetLocations("at")) {
            sink.blockChange(loc, material);
        }
    }
}
