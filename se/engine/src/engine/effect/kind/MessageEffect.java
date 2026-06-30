package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code MESSAGE} — canonical player-feedback primitive (§C): chat / actionbar / title. {@code channel} is
 * declared AFTER {@code text} so the terse {@code MESSAGE:<text>} parses as a chat line (default channel);
 * colon-bearing or title messages need the verbose form. {@code who} names the recipient(s) — the activating
 * player by default, but any party (e.g. {@code @Victim}) so a set proc can title the foe it hit. The
 * {@code {ATTACKER}}/{@code {VICTIM}} tokens expand to the activating player and the other combat party (the
 * same naming convention as the message-on-activate feature), so a recipient and a named party are independent.
 */
public final class MessageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MESSAGE")
            .param("text", D.STRING)
            .param("channel", D.enumOf("chat", "actionbar", "title").def("chat"))
            .param("subtitle", D.STRING.def(""), "title channel only")
            .param("fadeIn", D.TICKS.def(10), "title channel only")
            .param("stay", D.TICKS.def(70), "title channel only")
            .param("fadeOut", D.TICKS.def(20), "title channel only")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Send feedback on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade "
                    + "timings). Default recipient self; `who` can name any party (e.g. @Victim). The "
                    + "`{ATTACKER}`/`{VICTIM}` tokens expand to the activating player and the other combat party. "
                    + "Replaces ACTIONBAR/TITLE.")
            .example("{ MESSAGE: { text: \"&aCritical hit!\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String channel = ctx.str("channel");
        boolean title = "title".equalsIgnoreCase(channel);
        boolean actionbar = !title && "actionbar".equalsIgnoreCase(channel);
        String text = fill(ctx, ctx.str("text"));
        // Read the title-only params lazily — a chat/actionbar line never declares them.
        String subtitle = title ? fill(ctx, ctx.str("subtitle")) : null;
        int fadeIn = title ? ctx.integer("fadeIn") : 0;
        int stay = title ? ctx.integer("stay") : 0;
        int fadeOut = title ? ctx.integer("fadeOut") : 0;
        for (LivingEntity who : ctx.targets("who")) {
            if (!(who instanceof Player recipient)) {
                continue; // chat / actionbar / title all need a player recipient
            }
            if (title) {
                sink.title(recipient, text, subtitle, fadeIn, stay, fadeOut);
            } else if (actionbar) {
                sink.actionBar(recipient, text);
            } else {
                sink.message(recipient, text);
            }
        }
    }

    /**
     * Substitute the combat-party name tokens, leaving colour codes for the Sink to translate. Short-circuits
     * when there is no token to fill so a plain line never touches the (possibly null) actor/victim.
     */
    private static String fill(EffectCtx ctx, String s) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        String attacker = ctx.actor() == null ? "" : ctx.actor().getName();
        LivingEntity victim = ctx.victim();
        String victimName = victim == null ? "" : victim.getName();
        return s.replace("{ATTACKER}", attacker).replace("{VICTIM}", victimName);
    }
}
