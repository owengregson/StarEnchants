package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import engine.stores.RepeatStore;
import item.worn.WornState;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * Drives {@code REPEATING} abilities (§B): one entity-owned repeating task per {@code (player, ability)},
 * each on its own {@code repeat:} period in ticks ({@link Ability#repeatTicks()}).
 *
 * <p>Folia-correct: tasks run via {@link Scheduling#repeatingEntity} and follow the player across regions.
 * The {@link RepeatStore} owns the {@code (player, abilityId) → handle} map (concurrent) but never cancels —
 * this driver cancels each handle on the correct thread (store contract, §5.4). Must run on the player's own thread.
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

    /**
     * (Re)arm {@code player}'s repeating abilities from {@code worn}: cancel the ones no longer worn, schedule
     * the newly worn, and LEAVE the unchanged ones running.
     *
     * <p>The diff is the point. {@code arm} fires on every equipment refresh — including a plain hotbar
     * scroll — and a fresh task's first run is one whole period out, so cancel-and-reschedule let ordinary PvP
     * churn starve a 20-tick ward that holds a 60-tick immunity window open. An ability's id encodes its level
     * and its content, and a reload disarms everything, so "same id" is a sound identity to keep.
     */
    public void arm(Player player, WornState worn) {
        UUID id = player.getUniqueId();
        if (repeatingTrigger < 0 || worn == null) {
            disarm(id);
            return;
        }
        Ability[] abilities = content.snapshot().abilities();
        Set<Integer> desired = new LinkedHashSet<>(); // authored order, so tasks arm deterministically
        for (int abilityId : worn.byTrigger(repeatingTrigger)) {
            // De-dup: one task per ability, not per worn piece.
            if (abilityId >= 0 && abilityId < abilities.length && abilities[abilityId].repeatTicks() > 0) {
                desired.add(abilityId);
            }
        }
        for (int abilityId : store.liveIds(id)) {
            if (!desired.contains(abilityId)) {
                store.remove(id, abilityId).ifPresent(TaskHandle::cancel);
            }
        }
        for (int abilityId : desired) {
            if (store.has(id, abilityId)) {
                continue; // already running on its own phase — restarting it is exactly the bug
            }
            int period = abilities[abilityId].repeatTicks();
            // R-QC35b: `repeat-delay` moves the FIRST run off the period. Unset (-1) keeps the historical
            // shape, one full period out; 0 is clamped to 1, the earliest tick the scheduler can hold.
            int declared = abilities[abilityId].repeatDelayTicks();
            long firstRun = declared < 0 ? period : Math.max(1, declared);
            TaskHandle handle = Scheduling.repeatingEntity(player, firstRun, period,
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
