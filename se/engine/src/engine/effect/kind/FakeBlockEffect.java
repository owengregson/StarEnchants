package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code FAKE_BLOCK} — show a client-only block at each target's body/feet/eye to nearby players, then restore it. */
public final class FakeBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FAKE_BLOCK")
            .param("block", D.material())
            .param("duration", D.TICKS)
            .param("radius", D.DOUBLE.min(0).def(32))
            .param("anchor", D.enumOf("body", "feet", "eye").def("body"))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Show a client-only block at each target to players within the radius, then restore the real block.")
            .example("{ FAKE_BLOCK: { block: WATER, duration: 45, radius: 32, anchor: eye, who: '@Victim' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.fakeBlock(target, ctx.integer("block"), ctx.integer("duration"),
                    ctx.dbl("radius"), ctx.str("anchor"));
        }
    }
}
