package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.selector.kind.Allies;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.text.Tokens;
import schema.spec.D;

/**
 * {@code MESSAGE} — canonical player-feedback primitive (§C): chat / actionbar / title. {@code channel} is
 * declared AFTER {@code text} so the terse {@code MESSAGE:<text>} parses as a chat line (default channel);
 * colon-bearing or title messages need the verbose form. {@code who} names the recipient(s) — the activating
 * player by default, but any party (e.g. {@code @Victim}) so a set proc can title the foe it hit. The
 * {@code {ATTACKER}}/{@code {VICTIM}} tokens expand to the activating player and the other combat party (the
 * same naming convention as the message-on-activate feature), so a recipient and a named party are independent.
 * {@code {SELF}} is the third: the name of whoever is RECEIVING this copy, which on a per-target send differs
 * per recipient — the one token that cannot be filled once for the whole line.
 *
 * <p>{@code tokens} binds any further {@code {name}} placeholder to an EXPRESSION over the activation's facts,
 * evaluated per activation and rendered as a chat number. The bindings live in the parameter, never in the
 * text, so an authored line stays byte-verbatim; a placeholder with no binding is left literal, as before.
 *
 * <p>{@code {RELATION_COLOR}} is the second per-copy token: the colour for how THIS recipient stands to the
 * actor, drawn from {@code ally-color}/{@code enemy-color}. It follows the {@code tokens} rule rather than the
 * hex rule — the two colours live in params, so one authored line serves a whole mixed audience and the string
 * itself stays verbatim. The substituted value is a raw colour STRING (either spelling: {@code &c} or
 * {@code {#RRGGBB}}), which {@code Colors.translate} renders at the Sink, so ADR-0062's "one home for the
 * colour parse" is untouched — only the colour SELECTION is new, and it is contextual, which is precisely what
 * {@code Colors} cannot see.
 */
public final class MessageEffect implements EffectKind {

    /** The recipient-name token — filled per copy, so it can only be substituted inside the send loop. */
    private static final String SELF = "SELF";
    private static final String SELF_TOKEN = "{" + SELF + "}";

    /**
     * The per-recipient relation-colour token. {@code Tokens.sub} substitutes the hyphen spelling for free (any
     * key containing {@code _} gets the alias), so the PRESENCE scan has to look for both — a scan for the
     * underscore form alone leaves an authored {@code {RELATION-COLOR}} unsubstituted and printed literally,
     * which is the one failure the alias exists to prevent.
     */
    private static final String RELATION_COLOR = "RELATION_COLOR";
    private static final String RELATION_COLOR_TOKEN = "{" + RELATION_COLOR + "}";
    private static final String RELATION_COLOR_ALIAS = "{" + RELATION_COLOR.replace('_', '-') + "}";

    static final EffectSpec SPEC = EffectSpec.of("MESSAGE")
            .param("text", D.STRING)
            .param("channel", D.enumOf("chat", "actionbar", "title").def("chat"))
            .param("subtitle", D.STRING.def(""), "title channel only")
            .param("fadeIn", D.TICKS.def(10), "title channel only")
            .param("stay", D.TICKS.def(70), "title channel only")
            .param("fadeOut", D.TICKS.def(20), "title channel only")
            .param("tokens", D.exprMap(),
                    "name=expression bindings; each {name} in the text becomes the evaluated number")
            .param("ally-color", D.STRING.def("&a"), "the {RELATION_COLOR} value for a recipient allied to the actor")
            .param("enemy-color", D.STRING.def("&c"), "the {RELATION_COLOR} value for every other recipient")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Send feedback on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade "
                    + "timings). Default recipient self; `who` can name any party (e.g. @Victim). The "
                    + "`{ATTACKER}`/`{VICTIM}` tokens expand to the activating player and the other combat party, "
                    + "`{SELF}` to the name of whoever receives that copy, and `{RELATION_COLOR}` to "
                    + "`ally-color` or `enemy-color` depending on how that recipient stands to the actor — so one "
                    + "broadcast reads correctly to friend and foe. Replaces ACTIONBAR/TITLE.")
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
        // Evaluated ONCE per activation, not per recipient: a binding prices against the actor's facts, which
        // do not vary down the send loop. Empty (and allocation-free) for the lines that bind nothing.
        Map<String, Double> tokens = ctx.numbers("tokens");
        String text = fill(ctx, ctx.str("text"), tokens);
        // Read the title-only params lazily — a chat/actionbar line never declares them.
        String subtitle = title ? fill(ctx, ctx.str("subtitle"), tokens) : null;
        int fadeIn = title ? ctx.integer("fadeIn") : 0;
        int stay = title ? ctx.integer("stay") : 0;
        int fadeOut = title ? ctx.integer("fadeOut") : 0;
        // Decided ONCE, not per recipient: these two are the only tokens whose value varies down the loop, so a
        // line carrying neither pays two scans and re-substitutes nothing.
        boolean named = has(text, SELF_TOKEN) || has(subtitle, SELF_TOKEN);
        boolean coloured = hasRelationColor(text) || hasRelationColor(subtitle);
        Player actor = coloured ? ctx.actor() : null; // only the colour needs the actor as a relation SUBJECT
        String allyColor = coloured ? ctx.str("ally-color") : null;
        String enemyColor = coloured ? ctx.str("enemy-color") : null;
        for (LivingEntity who : ctx.targets("who")) {
            if (!(who instanceof Player recipient)) {
                continue; // chat / actionbar / title all need a player recipient
            }
            String line = text;
            String sub = subtitle;
            if (named) {
                String name = recipient.getName();
                line = Tokens.sub(line, SELF, name);
                sub = Tokens.sub(sub, SELF, name);
            }
            if (coloured) {
                String color = relationColor(actor, recipient, allyColor, enemyColor);
                line = Tokens.sub(line, RELATION_COLOR, color);
                sub = Tokens.sub(sub, RELATION_COLOR, color);
            }
            if (title) {
                sink.title(recipient, line, sub, fadeIn, stay, fadeOut);
            } else if (actionbar) {
                sink.actionBar(recipient, line);
            } else {
                sink.message(recipient, line);
            }
        }
    }

    /** Whether {@code s} carries {@code token} at all (a scan, never an allocation). */
    private static boolean has(String s, String token) {
        return s != null && s.contains(token);
    }

    /** Whether {@code s} carries the relation-colour token in EITHER spelling {@code Tokens.sub} would fill. */
    private static boolean hasRelationColor(String s) {
        return has(s, RELATION_COLOR_TOKEN) || has(s, RELATION_COLOR_ALIAS);
    }

    /**
     * The colour for how {@code recipient} stands to {@code actor} — the ONE installed alliance predicate
     * ({@code Allies}), the same axis {@code %victim.relation%} and the {@code ALLIES}/{@code ENEMIES} selector
     * filters read, so a line and the selector that chose its audience can never disagree.
     *
     * <p>The actor reading their OWN copy is an ally: {@code Allies.allied} answers false for a player against
     * themselves (it is a two-party question), and colouring the caster as their own enemy is never what a
     * broadcast means. With no actor at all there is no relation to read, so every recipient takes the enemy
     * colour — the same side {@code allied} degrades to when a faulty bridge throws.
     */
    private static String relationColor(Player actor, Player recipient, String allyColor, String enemyColor) {
        if (actor == null) {
            return enemyColor;
        }
        return actor.equals(recipient) || Allies.allied(actor, recipient) ? allyColor : enemyColor;
    }

    /**
     * Substitute the combat-party name tokens and the authored expression bindings, leaving colour codes for
     * the Sink to translate. Short-circuits when there is no token to fill so a plain line never touches the
     * (possibly null) actor/victim.
     */
    private static String fill(EffectCtx ctx, String s, Map<String, Double> tokens) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        String attacker = ctx.actor() == null ? "" : ctx.actor().getName();
        LivingEntity victim = ctx.victim();
        String victimName = victim == null ? "" : victim.getName();
        return Tokens.subNumbers(Tokens.sub(s, "ATTACKER", attacker, "VICTIM", victimName), tokens);
    }
}
