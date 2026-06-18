package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import engine.stores.RepeatStore;
import item.worn.WornState;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * Drives §B {@code REPEATING} abilities (docs/v3-directives.md §B): one entity-owned repeating task per
 * {@code (player, repeating ability)}, each firing on its own {@code repeat:} period
 * ({@link Ability#repeatTicks()}). Armed on every equip change (the {@code EquipListener} calls
 * {@link #arm} after refreshing the {@link WornState}) and torn down on quit ({@link #disarm}) / disable
 * ({@link #disarmAll}).
 *
 * <p><strong>Folia-correct.</strong> Tasks run on the player's region thread via
 * {@link Scheduling#repeatingEntity} and follow the player across regions/teleports; the per-tick
 * {@link feature.trigger.TriggerDispatch#fireRepeating} builds and flushes its own sink there. The
 * {@link RepeatStore} owns the {@code (player, abilityId) → handle} mapping (concurrent) but never cancels —
 * this driver cancels each returned handle on the correct thread (the store's contract, §5.4).
 *
 * <p><strong>Lifecycle.</strong> {@link #arm} disarms the player's existing tasks then re-schedules from the
 * fresh {@code WornState} — disarm-then-rearm, so a no-longer-worn repeating ability's task is cancelled and
 * a re-periodised one is rescheduled with no diff bookkeeping. List-multiplicity is de-duped (one task per
 * ability, not per worn piece). Must be called on the player's own thread (the equip events are player-owned).
 */
public final class RepeatingDriver {

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final int repeatingTrigger;
    private final RepeatStore<TaskHandle> store;

    public RepeatingDriver(TriggerDispatch dispatch, ContentHolder content, int repeatingTrigger,
                           RepeatStore<TaskHandle> store) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.repeatingTrigger = repeatingTrigger;
        this.store = Objects.requireNonNull(store, "store");
    }

    /** (Re)arm {@code player}'s repeating abilities from {@code worn}; disarms first so dropped ones stop. */
    public void arm(Player player, WornState worn) {
        UUID id = player.getUniqueId();
        disarm(id);
        if (repeatingTrigger < 0 || worn == null) {
            return;
        }
        Ability[] abilities = content.snapshot().abilities();
        int[] candidates = worn.byTrigger(repeatingTrigger);
        Set<Integer> armed = new HashSet<>();
        for (int abilityId : candidates) {
            if (abilityId < 0 || abilityId >= abilities.length || !armed.add(abilityId)) {
                continue; // de-dup list-multiplicity: one task per ability, not per worn piece
            }
            int period = abilities[abilityId].repeatTicks();
            if (period <= 0) {
                continue; // a REPEATING ability with no repeat period has nothing to schedule
            }
            TaskHandle handle = Scheduling.repeatingEntity(player, period, period,
                    () -> dispatch.fireRepeating(player, abilityId));
            store.put(id, abilityId, handle).ifPresent(TaskHandle::cancel);
        }
    }

    /** Cancel + forget all of one player's repeating tasks (call on quit / before re-arm). */
    public void disarm(UUID player) {
        store.removeAll(player).forEach(TaskHandle::cancel);
    }

    /** Cancel + forget every repeating task across all players (call on disable / reload). */
    public void disarmAll() {
        store.removeEverything().forEach(TaskHandle::cancel);
    }
}
