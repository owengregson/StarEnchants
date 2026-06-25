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
 * {@code SET_BLOCK} — set the target block(s) to a material (§C/§A); {@code material} interned at compile (§9).
 * Slot defaults to {@code @Here} and accepts any block/location selector inline (e.g. {@code @Trench}).
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
