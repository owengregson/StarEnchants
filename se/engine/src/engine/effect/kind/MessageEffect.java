package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code MESSAGE} — the canonical player-feedback primitive (docs/v3-directives.md §C), collapsing
 * {@code ACTIONBAR} and {@code TITLE} into one kind via a {@code channel}:
 *
 * <ul>
 *   <li>{@code chat} (default) — a chat line;</li>
 *   <li>{@code actionbar} — the action bar;</li>
 *   <li>{@code title} — a centered title + {@code subtitle}, with {@code fadeIn}/{@code stay}/{@code fadeOut}
 *       timings (ticks). For the title channel, {@code text} is the title line.</li>
 * </ul>
 *
 * <p><strong>Terse colon-safety.</strong> {@code channel} is optional and declared AFTER {@code text}, so
 * the legacy terse form {@code MESSAGE:<text>} still parses as a chat message (channel defaults to
 * {@code chat}) — every existing content line and migrator emission keeps working unchanged. The
 * pre-existing "free text cannot contain a top-level {@code :} in terse form" limitation is unchanged;
 * colon-bearing or title messages use the verbose form. {@link Affinity#CONTEXT_LOCAL}: applies on the
 * firing thread for the activating player.
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
