package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code BREAK_BLOCK} — break the target block(s) (§C/§A). Slot defaults to {@code @Here} and accepts any
 * block/location selector inline (e.g. {@code @Vein}/{@code @Tunnel}/{@code @Trench}).
 */
public final class BreakBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BREAK_BLOCK")
            .param("drops", D.BOOL.def(true))
            .param("void-materials", D.materials().def(""),
                    "these block types break WITHOUT drops even when drops is true (empty = none)")
            .target("at", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Break the target block(s) (default @Here; drops=false clears). "
                    + "@Vein/@Tunnel/@Trench/@Bore for shapes. void-materials is the per-block exception to "
                    + "`drops`: the listed types are destroyed dropless while everything else in the same "
                    + "volume still yields, which is how a bulk excavator keeps the ore and voids the stone.")
            .example("{ BREAK_BLOCK: { drops: true } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        boolean drops = ctx.bool("drops");
        List<Integer> voided = drops ? ctx.args().ids("void-materials") : List.of();
        for (Location loc : ctx.targetLocations("at")) {
            sink.breakBlock(loc, drops, voided);
        }
    }
}
