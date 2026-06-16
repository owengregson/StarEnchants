package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code ACTIONBAR} — show an action-bar message to the activating player
 * (docs/architecture.md §7). Stateless; emits one {@code actionBar} intent for the
 * actor and never touches the player directly. {@link Affinity#CONTEXT_LOCAL}: it
 * applies on the firing thread.
 */
public final class ActionBarEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ACTIONBAR")
            .param("text", D.STRING)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Show an action-bar message to the activating player.")
            .example("ACTIONBAR:&eCharged")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.actionBar(ctx.actor(), ctx.str("text"));
    }
}
