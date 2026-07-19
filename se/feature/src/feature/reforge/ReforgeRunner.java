package feature.reforge;

import compile.load.ContentHolder;
import compile.load.ReforgeDef;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.effect.kind.DisarmShuffleEffect;
import engine.effect.kind.HitTempoEffect;
import engine.run.UseAttempt;
import engine.stores.EngineStores;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;

/**
 * The reforge activation tail (ADR-0070): resolve the held weapon's stamped key to its live def, fire the
 * def's USE abilities through the SAME pipeline every source uses ({@link TriggerDispatch#fireUse} — the full
 * gate sequence: the cooldown arms at gate 6 on the ability's own {@code cooldown:}), and speak through the
 * universal {@link ReforgeMessenger} — the pets convention 1:1: activated on success, on-cooldown / fail on
 * those gates, and the universal ENDED when a timed window (Berry Overdrive's tempo, The Ace's one-shot)
 * closes, scheduled here at activation and store-liveness-guarded at fire time. A stale key (content
 * removed mid-session) is a silent no-op — there is no def left to name.
 *
 * <p>On the ACTIVATED outcome, after the success message, this calls the cross-plan {@code onActivated} hook
 * (Plan B's {@code ReforgeMachines}, §B0.4): the movement/space machines only run because the runner routes
 * the activated def's effects to them here — deliberately NOT the global activation-listener chain (which
 * would need source-kind filtering in shared plumbing; the pets precedent keeps it local).
 */
public final class ReforgeRunner {

    private final ContentHolder content;
    private final TriggerDispatch dispatch;
    private final ReforgeMessenger messenger;
    private final BiConsumer<Player, ReforgeDef> onActivated; // §B0.4 cross-plan seam — Plan B's ReforgeMachines
    private final EngineStores stores;   // the tempo/disarm window liveness reads behind the ENDED message
    private final LongSupplier nowTicks;

    public ReforgeRunner(ContentHolder content, TriggerDispatch dispatch, ReforgeMessenger messenger,
                         BiConsumer<Player, ReforgeDef> onActivated, EngineStores stores, LongSupplier nowTicks) {
        this.content = Objects.requireNonNull(content, "content");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
        this.onActivated = Objects.requireNonNull(onActivated, "onActivated");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    void activate(Player player, String key) {
        ReforgeDef def = content.library().reforgeDefOf(key);
        if (def == null) {
            return; // stale key (content removed) — nothing left to name; the gesture stays claimed
        }
        UseAttempt attempt = dispatch.fireUse(player, def.useStableKeys());
        if (attempt.activated()) {
            messenger.activated(player, def);
            onActivated.accept(player, def); // §B0.4: the machines run at the ACTIVATED outcome, after feedback
            scheduleEnded(player, def);
            return;
        }
        if (attempt.onCooldown()) {
            messenger.onCooldown(player, def, attempt.cooldownRemainingTicks());
            return;
        }
        if (attempt.conditionCandidateIndex() >= 0) {
            messenger.failed(player, def);
            return;
        }
        // CHANCE_FAILED / BLOCKED — silent (the use-item convention; contracts §3 spendCooldownOnChanceFail).
    }

    /**
     * Schedule the universal ENDED for a timed armed window (the pets armHome idiom): read the def's
     * HIT_TEMPO / DISARM_SHUFFLE durations from the CURRENT snapshot, then check store liveness one tick
     * past the window — a re-activation inside the window keeps its own later check, so the message fires
     * once, at the close of the LAST window. Instant reforges (no window) schedule nothing; the Star
     * Battery's close is its own discharge announcement.
     */
    private void scheduleEnded(Player player, ReforgeDef def) {
        Snapshot snapshot = content.snapshot();
        int window = 0;
        boolean tempo = false;
        boolean disarm = false;
        for (String key : def.useStableKeys()) {
            Ability ability = snapshot.byStableKey(key);
            if (ability == null) {
                continue;
            }
            for (CompiledEffect effect : ability.effects()) {
                if (HitTempoEffect.HEAD.equals(effect.head())) {
                    tempo = true;
                    window = Math.max(window, effect.args().integer("duration"));
                } else if (DisarmShuffleEffect.HEAD.equals(effect.head())) {
                    disarm = true;
                    window = Math.max(window, effect.args().integer("duration"));
                }
            }
        }
        if (window <= 0) {
            return;
        }
        boolean readTempo = tempo;
        boolean readDisarm = disarm;
        UUID id = player.getUniqueId();
        Scheduling.onEntityLater(player, window + 1L, () -> {
            long now = nowTicks.getAsLong();
            boolean live = (readTempo && stores.hitTempo().window(id, now) != null)
                    || (readDisarm && stores.disarmWindows().armed(id, now) != null);
            if (!live) {
                messenger.ended(player, def);
            }
        });
    }
}
