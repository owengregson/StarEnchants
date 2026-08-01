package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectHalt;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.entity.Player;
import schema.spec.D;

/** {@code REQUIRE_SOUL_TOTAL} — halt unless the actor's live post-spend soul total matches a modulo gate. */
public final class RequireSoulTotalEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REQUIRE_SOUL_TOTAL")
            .param("divisor", D.INT.min(1).def(20))
            .param("remainder", D.INT.min(0).def(0))
            .param("require-paid", D.BOOL.def(false))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Continue only when the actor's authoritative current soul total modulo divisor equals remainder; require-paid also rejects a live soul-cost waiver.")
            .example("{ REQUIRE_SOUL_TOTAL: { divisor: 20, remainder: 0, require-paid: true } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        int divisor = ctx.integer("divisor");
        int remainder = ctx.integer("remainder");
        if (actor == null || remainder >= divisor
                || (ctx.bool("require-paid") && sink.soulCostFree(actor))
                || Math.floorMod(sink.soulTotal(actor), divisor) != remainder) {
            throw EffectHalt.INSTANCE;
        }
    }
}
