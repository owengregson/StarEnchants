package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.text.Tokens;
import schema.spec.D;

/** {@code RUN_COMMAND} — run a command as the console or as the activating player (§7). */
public final class RunCommandEffect implements EffectKind {

    private static final Logger LOG = System.getLogger("StarEnchants.RunCommand");

    static final EffectSpec SPEC = EffectSpec.of("RUN_COMMAND")
            .param("command", D.STRING)
            .param("as", D.enumOf("console", "player").def("console"), "who runs it: console (default) or the player")
            .affinity(Affinity.GLOBAL)
            .doc("Run a command as the console (default) or as the activating player. The `{PLAYER}`/`{UUID}`/"
                    + "`{WORLD}` tokens expand to the actor's name, uuid, and world, and `{VICTIM}` to the other "
                    + "combat party's name (empty on a victimless activation). Affinity GLOBAL — the console "
                    + "path runs on the global thread; the player path runs on the actor's own thread. "
                    + "`{PLAYER}` and `{VICTIM}` both refuse to run the command when the name they would embed "
                    + "falls outside the standard `[A-Za-z0-9_]` (1-16) username charset.")
            .example("{ RUN_COMMAND: { command: \"f focus {VICTIM}\", as: player } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String command = fill(ctx, ctx.str("command"));
        if (command == null) {
            return; // {PLAYER} refused a non-standard username — dispatch nothing (console AND player paths)
        }
        if ("player".equalsIgnoreCase(ctx.str("as"))) {
            sink.playerCommand(ctx.actor(), command);
        } else {
            sink.consoleCommand(command);
        }
    }

    /**
     * Substitute the actor and victim tokens, mirroring {@link MessageEffect#fill}. Short-circuits when there is
     * no token so a plain command never touches the (possibly null) actor. RUN_COMMAND is cold (GLOBAL affinity),
     * so the live reads here are off the combat hot path. Returns {@code null} to REFUSE the command when
     * {@code {PLAYER}} or {@code {VICTIM}} would embed a non-standard username ({@code {UUID}}/{@code {WORLD}}
     * are server-/operator-authored, so neither is player-influenced).
     *
     * <p>{@code {VICTIM}} (R-QC43) is what makes a per-hit bridge command authorable at all: the effect takes no
     * {@code who} target, so before it the struck player could not be named and the command ran on the literal.
     * A victimless activation fills it EMPTY rather than refusing — a command that reads it on the wrong trigger
     * is an authoring error the operator sees in their own plugin, not something to silence a whole enchant over.
     */
    private static String fill(EffectCtx ctx, String s) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        Player actor = ctx.actor();
        if (actor != null && s.contains("{PLAYER}") && !safeName(actor.getName())) {
            // Refuse, never strip: a stripped name ("Notch " → "Notch") could collapse onto a DIFFERENT real player
            // and mis-target the command. Only reachable on offline-mode/proxy setups permitting crafted names; log
            // the length, never re-embed the raw name.
            LOG.log(Level.WARNING, "RUN_COMMAND refused: {PLAYER} name outside [A-Za-z0-9_] (1-16 chars), length "
                    + actor.getName().length());
            return null;
        }
        LivingEntity victim = ctx.victim();
        String victimName = victim == null ? "" : victim.getName();
        // A mob's name is not a username at all (and may be an authored custom name), so the charset test guards
        // {VICTIM} exactly as it guards {PLAYER} — the token is only ever meant to address a struck PLAYER.
        if (s.contains("{VICTIM}") && !victimName.isEmpty() && !safeName(victimName)) {
            LOG.log(Level.WARNING, "RUN_COMMAND refused: {VICTIM} name outside [A-Za-z0-9_] (1-16 chars), length "
                    + victimName.length());
            return null;
        }
        String name = actor == null ? "" : actor.getName();
        String uuid = actor == null ? "" : actor.getUniqueId().toString();
        String world = actor == null || actor.getWorld() == null ? "" : actor.getWorld().getName();
        return Tokens.sub(s, "PLAYER", name, "UUID", uuid, "WORLD", world, "VICTIM", victimName);
    }

    /** The Mojang username charset: 1-16 chars, each {@code [A-Za-z0-9_]}. No regex alloc (cold path but shared). */
    static boolean safeName(String s) {
        if (s == null || s.isEmpty() || s.length() > 16) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
