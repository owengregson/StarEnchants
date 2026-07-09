package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
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
                    + "`{WORLD}` tokens expand to the actor's name, uuid, and world. Affinity GLOBAL — the console "
                    + "path runs on the global thread; the player path runs on the actor's own thread. The "
                    + "`{PLAYER}` token refuses to run the command when the actor's name falls outside the standard "
                    + "`[A-Za-z0-9_]` (1-16) username charset.")
            .example("{ RUN_COMMAND: { command: \"eco give {PLAYER} 100\" } }")
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
     * Substitute the actor tokens, mirroring {@link MessageEffect#fill}. Short-circuits when there is no token so
     * a plain command never touches the (possibly null) actor. RUN_COMMAND is cold (GLOBAL affinity), so the live
     * actor reads here are off the combat hot path. Returns {@code null} to REFUSE the command when its
     * {@code {PLAYER}} token would embed a non-standard username ({@code {UUID}}/{@code {WORLD}} are
     * server-/operator-authored, so neither is player-influenced).
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
        String name = actor == null ? "" : actor.getName();
        String uuid = actor == null ? "" : actor.getUniqueId().toString();
        String world = actor == null || actor.getWorld() == null ? "" : actor.getWorld().getName();
        return Tokens.sub(s, "PLAYER", name, "UUID", uuid, "WORLD", world);
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
