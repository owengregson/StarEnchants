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
 * {@code BREAK_BLOCK} — break the target block(s) (§C/§A). Slot defaults to {@code @Here} and accepts any
 * block/location selector inline (e.g. {@code @Vein}/{@code @Tunnel}/{@code @Trench}).
 */
public final class BreakBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BREAK_BLOCK")
            .param("drops", D.BOOL.def(true))
            .param("use-held-tool", D.BOOL.def(false))
            .target("at", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Break the target block(s) (default @Here; drops=false clears). @Vein/@Tunnel/@Trench for shapes.")
            .example("{ BREAK_BLOCK: { drops: true } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        boolean drops = ctx.bool("drops");
        boolean heldTool = ctx.args().has("use-held-tool") && ctx.bool("use-held-tool");
        for (Location loc : ctx.targetLocations("at")) {
            if (heldTool) {
                sink.breakBlockWithTool(loc, drops, ctx.actor());
            } else {
                sink.breakBlock(loc, drops);
            }
        }
    }
}
