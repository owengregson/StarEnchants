package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code MESSAGE} — canonical player-feedback primitive (§C): chat / actionbar / title. {@code channel} is
 * declared AFTER {@code text} so the terse {@code MESSAGE:<text>} parses as a chat line (default channel);
 * colon-bearing or title messages need the verbose form.
 */
public final class MessageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MESSAGE")
            .param("text", D.STRING)
            .param("channel", D.enumOf("chat", "actionbar", "title").def("chat"))
            .param("subtitle", D.STRING.def(""), "title channel only")
            .param("fadeIn", D.TICKS.def(10), "title channel only")
            .param("stay", D.TICKS.def(70), "title channel only")
            .param("fadeOut", D.TICKS.def(20), "title channel only")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Send feedback to the activating player on a channel: chat (default), actionbar, or title "
                    + "(with subtitle + fade/stay/fade timings). Replaces ACTIONBAR/TITLE.")
            .example("MESSAGE:&aCritical hit!")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String channel = ctx.str("channel");
        if ("title".equalsIgnoreCase(channel)) {
            sink.title(ctx.actor(), ctx.str("text"), ctx.str("subtitle"),
                    ctx.integer("fadeIn"), ctx.integer("stay"), ctx.integer("fadeOut"));
        } else if ("actionbar".equalsIgnoreCase(channel)) {
            sink.actionBar(ctx.actor(), ctx.str("text"));
        } else {
            sink.message(ctx.actor(), ctx.str("text"));
        }
    }
}
