package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Iterator;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code BLOCK_BREAK_EFFECT} — Bukkit effect 2001 / STEP_SOUND with exact block material data. */
public final class BlockBreakEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BLOCK_BREAK_EFFECT")
            .param("block", D.material())
            .param("anchor", D.enumOf("body", "feet", "eye").def("feet"))
            .param("y-offset", D.DOUBLE.def(0.0))
            .param("once-at-actor", D.BOOL.def(false))
            .target("who", T.HERE)
            .affinity(Affinity.REGION)
            .actorOrigin()
            .doc("Play the combined block-break particles and sound used by Bukkit STEP_SOUND/effect 2001. "
                    + "once-at-actor emits one cue at the captured actor origin only when 'who' matched.")
            .example("{ BLOCK_BREAK_EFFECT: { block: DIAMOND_BLOCK, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int block = ctx.integer("block");
        Iterator<LivingEntity> targets = ctx.targets("who").iterator();
        if (ctx.args().has("once-at-actor") && ctx.bool("once-at-actor")) {
            if (!targets.hasNext()) {
                return;
            }
            org.bukkit.Location at = "eye".equalsIgnoreCase(ctx.str("anchor"))
                    ? ctx.actorOriginEye() : ctx.actorOrigin();
            if (at != null) {
                at.add(0.0, ctx.dbl("y-offset"), 0.0);
                sink.blockBreakEffect(at, block);
            }
            return;
        }
        if (targets.hasNext()) {
            do {
                sink.blockBreakEffect(targets.next(), block, ctx.str("anchor"), ctx.dbl("y-offset"));
            } while (targets.hasNext());
        } else if (ctx.location() != null) {
            sink.blockBreakEffect(ctx.location(), block);
        }
    }
}
