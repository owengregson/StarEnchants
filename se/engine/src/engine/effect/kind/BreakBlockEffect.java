package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code BREAK_BLOCK} — break the block at the activation location (docs/v3-directives.md §C). Stateless;
 * emits one {@code breakBlock} intent and never touches the world directly. {@code drops} (default true)
 * controls whether the block yields its drops or is simply cleared. No-op when there is no activation
 * location. {@link Affinity#REGION}: the break routes to the region thread owning the location.
 */
public final class BreakBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BREAK_BLOCK")
            .param("drops", D.BOOL.def(true))
            .affinity(Affinity.REGION)
            .doc("Break the block at the activation location (drops=false clears it). No-op if there is no location.")
            .example("BREAK_BLOCK:true")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Location loc = ctx.location();
        if (loc != null) {
            sink.breakBlock(loc, ctx.bool("drops"));
        }
    }
}
