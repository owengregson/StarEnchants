package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import org.bukkit.entity.Player;
import platform.text.Tokens;
import schema.spec.D;

/** {@code RUN_COMMAND} — run a command as the console or as the activating player (§7). */
public final class RunCommandEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("RUN_COMMAND")
            .param("command", D.STRING)
            .param("as", D.enumOf("console", "player").def("console"), "who runs it: console (default) or the player")
            .affinity(Affinity.GLOBAL)
            .doc("Run a command as the console (default) or as the activating player. The `{PLAYER}`/`{UUID}`/"
                    + "`{WORLD}` tokens expand to the actor's name, uuid, and world. Affinity GLOBAL — the console "
                    + "path runs on the global thread; the player path runs on the actor's own thread.")
            .example("{ RUN_COMMAND: { command: \"eco give {PLAYER} 100\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String command = fill(ctx, ctx.str("command"));
        if ("player".equalsIgnoreCase(ctx.str("as"))) {
            sink.playerCommand(ctx.actor(), command);
        } else {
            sink.consoleCommand(command);
        }
    }

    /**
     * Substitute the actor tokens, mirroring {@link MessageEffect#fill}. Short-circuits when there is no token so
     * a plain command never touches the (possibly null) actor. RUN_COMMAND is cold (GLOBAL affinity), so the live
     * actor reads here are off the combat hot path.
     */
    private static String fill(EffectCtx ctx, String s) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        Player actor = ctx.actor();
        String name = actor == null ? "" : actor.getName();
        String uuid = actor == null ? "" : actor.getUniqueId().toString();
        String world = actor == null || actor.getWorld() == null ? "" : actor.getWorld().getName();
        return Tokens.sub(s, "PLAYER", name, "UUID", uuid, "WORLD", world);
    }
}
