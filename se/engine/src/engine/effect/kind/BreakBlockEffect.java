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
 * {@code BREAK_BLOCK} — break one or more blocks (docs/v3-directives.md §C/§A). Stateless; emits a
 * {@code breakBlock} intent per target location and never touches the world directly. {@code drops}
 * (default true) controls whether each block yields its drops or is simply cleared. The target slot
 * defaults to {@code @Here} (the activation block, so the bare {@code BREAK_BLOCK} is unchanged) and accepts
 * any block/location selector inline — e.g. {@code BREAK_BLOCK:true:@Vein} clears an ore vein, or
 * {@code @Tunnel}/{@code @Trench} for mining shapes. {@link Affinity#REGION}: each break routes to the
 * region thread owning its location.
 */
public final class BreakBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BREAK_BLOCK")
            .param("drops", D.BOOL.def(true))
            .target("at", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Break the target block(s) (default @Here; drops=false clears). @Vein/@Tunnel/@Trench for shapes.")
            .example("BREAK_BLOCK:true")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        boolean drops = ctx.bool("drops");
        for (Location loc : ctx.targetLocations("at")) {
            sink.breakBlock(loc, drops);
        }
    }
}
