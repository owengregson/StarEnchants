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
            .param("smelt", D.INT.min(0).max(64).def(0),
                    "smelted products per smeltable block in the volume; 0 = no transform")
            .param("smelt-materials", D.materials().def(""),
                    "restrict the smelt transform to these block types (empty = every type that smelts)")
            .target("at", T.HERE)
            .affinity(Affinity.REGION)
            .doc("Break the target block(s) (default @Here; drops=false clears). "
                    + "@Vein/@Tunnel/@Trench/@Bore for shapes. void-materials is the per-block exception to "
                    + "`drops`: the listed types are destroyed dropless while everything else in the same "
                    + "volume still yields, which is how a bulk excavator keeps the ore and voids the stone. "
                    + "`smelt` is the volume's drop TRANSFORM — the excavation twin of the MINE-scoped SMELT "
                    + "read-back, which only ever addresses the one block a MINE event names: a smeltable "
                    + "block yields that many of its smelted product instead of its raw drop. Being a number "
                    + "rather than a flag, it takes a fact expression, so a co-enchant rule ('only alongside "
                    + "Fuse') is one authored product and needs no second ability.")
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
        int smelt = drops ? ctx.integer("smelt") : 0;
        List<Integer> smeltable = smelt > 0 ? ctx.args().ids("smelt-materials") : List.of();
        for (Location loc : ctx.targetLocations("at")) {
            sink.breakBlock(loc, drops, voided, smelt, smeltable);
        }
    }
}
