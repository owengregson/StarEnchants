package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code MESSAGE} — send a chat message to the activating player (docs/architecture.md §7).
 * Stateless; emits a {@code message} intent for the actor and never touches the player
 * directly. {@link Affinity#CONTEXT_LOCAL}: it applies on the firing thread.
 */
public final class MessageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MESSAGE")
            .param("text", D.STRING)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Send a chat message to the activating player.")
            .example("MESSAGE:&aCritical hit!")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.message(ctx.actor(), ctx.str("text"));
    }
}
