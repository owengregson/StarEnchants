package feature.reforge;

import compile.load.ContentHolder;
import compile.load.ReforgeDef;
import engine.run.UseAttempt;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import platform.lang.Messages;
import platform.text.Colors;
import platform.text.TimeFormat;

/**
 * The reforge activation tail (ADR-0070): resolve the held weapon's stamped key to its live def, fire the
 * def's USE abilities through the SAME pipeline every source uses ({@link TriggerDispatch#fireUse} — the full
 * gate sequence: the cooldown arms at gate 6 on the ability's own {@code cooldown:}), and render the universal
 * prefix-free feedback (the use-item trio). A stale key (content removed) is an explained no-op.
 *
 * <p>On the ACTIVATED outcome, after the success feedback, this calls the cross-plan {@code onActivated} hook
 * (Plan B's {@code ReforgeMachines}, §B0.4): the movement/space machines only run because the runner routes
 * the activated def's effects to them here — deliberately NOT the global activation-listener chain (which
 * would need source-kind filtering in shared plumbing; the pets precedent keeps it local).
 */
public final class ReforgeRunner {

    private final ContentHolder content;
    private final TriggerDispatch dispatch;
    private final Messages messages;
    private final BiConsumer<Player, ReforgeDef> onActivated; // §B0.4 cross-plan seam — Plan B's ReforgeMachines

    public ReforgeRunner(ContentHolder content, TriggerDispatch dispatch, Messages messages,
                         BiConsumer<Player, ReforgeDef> onActivated) {
        this.content = Objects.requireNonNull(content, "content");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onActivated = Objects.requireNonNull(onActivated, "onActivated");
    }

    void activate(Player player, String key) {
        ReforgeDef def = content.library().reforgeDefOf(key);
        if (def == null) {
            sendUniversal(player, "reforge.fail", "NAME", "", "CONDITION", "");
            return;
        }
        UseAttempt attempt = dispatch.fireUse(player, def.useStableKeys());
        String name = def.color() + "&l" + def.display();
        if (attempt.activated()) {
            sendUniversal(player, "reforge.success", "NAME", name);
            onActivated.accept(player, def); // §B0.4: the machines run at the ACTIVATED outcome, after feedback
            return;
        }
        if (attempt.onCooldown()) {
            sendUniversal(player, "reforge.cooldown", "NAME", name,
                    "TIME_FORMATTED", TimeFormat.hmsFromTicks(attempt.cooldownRemainingTicks()));
            return;
        }
        int index = attempt.conditionCandidateIndex();
        if (index >= 0) {
            String source = index < def.conditionSources().size() ? def.conditionSources().get(index) : "";
            sendUniversal(player, "reforge.fail", "NAME", name, "CONDITION", source);
            return;
        }
        // CHANCE_FAILED / BLOCKED — silent (the use-item convention; contracts §3 spendCooldownOnChanceFail).
    }

    /** Render a UNIVERSAL reforge message: no prefix (fragment), fill tokens, translate, send; empty ⇒ silent. */
    private void sendUniversal(Player player, String key, Object... tokens) {
        messages.sendText(player, Colors.translate(messages.fragment(key, tokens)));
    }
}
