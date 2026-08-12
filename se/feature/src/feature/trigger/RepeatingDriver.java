package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
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
 * The {@link RepeatStore} owns the {@code (player, abilityId) → }{@link Armed} map (concurrent) but never cancels —
 * this driver cancels each handle on the correct thread (store contract, §5.4). Must run on the player's own thread.
 */
public final class RepeatingDriver {

    /**
     * One armed task: its handle plus the snapshot generation it was scheduled against. The generation is the
     * re-arm key — an ability's dense id survives a reload while its {@code repeat:}/{@code repeat-delay:} (or
     * the content behind that id) may not, so a task from an older generation is always rescheduled.
     */
    public record Armed(TaskHandle handle, int generation) {
    }

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final int repeatingTrigger;
    private final RepeatStore<Armed> store;

    public RepeatingDriver(TriggerDispatch dispatch, ContentHolder content, int repeatingTrigger,
                           RepeatStore<Armed> store) {
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
     * churn starve a 20-tick ward that holds a 60-tick immunity window open.
     *
     * <p>What is kept must be proven live, not merely present: on Folia an entity task is RETIRED when its
     * entity is removed, so after a death the store still holds handles whose bodies will never run again, and
     * a presence-only check would leave every REPEATING ward dead until the player relogged. The handle
     * reports that ({@link TaskHandle#isCancelled()}); a retired one is rescheduled like a fresh id.
     */
    public void arm(Player player, WornState worn) {
        UUID id = player.getUniqueId();
        if (repeatingTrigger < 0 || worn == null) {
            disarm(id);
            return;
        }
        Snapshot snapshot = content.snapshot();
        int generation = snapshot.generation();
        Ability[] abilities = snapshot.abilities();
        Set<Integer> desired = new LinkedHashSet<>(); // authored order, so tasks arm deterministically
        for (int abilityId : worn.byTrigger(repeatingTrigger)) {
            // De-dup: one task per ability, not per worn piece.
            if (abilityId >= 0 && abilityId < abilities.length && abilities[abilityId].repeatTicks() > 0) {
                desired.add(abilityId);
            }
        }
        for (int abilityId : store.liveIds(id)) {
            if (!desired.contains(abilityId)) {
                store.remove(id, abilityId).ifPresent(RepeatingDriver::cancel);
            }
        }
        for (int abilityId : desired) {
            Armed live = store.get(id, abilityId).orElse(null);
            if (live != null && live.generation() == generation && !live.handle().isCancelled()) {
                continue; // already running on its own phase — restarting it is exactly the bug
            }
            int period = abilities[abilityId].repeatTicks();
            // R-QC35b: `repeat-delay` moves the FIRST run off the period. Unset (-1) keeps the historical
            // shape, one full period out; 0 is clamped to 1, the earliest tick the scheduler can hold.
            int declared = abilities[abilityId].repeatDelayTicks();
            long firstRun = declared < 0 ? period : Math.max(1, declared);
            TaskHandle handle = Scheduling.repeatingEntity(player, firstRun, period,
                    () -> dispatch.fireRepeating(player, abilityId));
            store.put(id, abilityId, new Armed(handle, generation)).ifPresent(RepeatingDriver::cancel);
        }
    }

    /** Cancel + forget all of one player's repeating tasks (call on quit / before re-arm). */
    public void disarm(UUID player) {
        store.removeAll(player).forEach(RepeatingDriver::cancel);
    }

    /** Cancel + forget every repeating task across all players (call on disable / reload). */
    public void disarmAll() {
        store.removeEverything().forEach(RepeatingDriver::cancel);
    }

    private static void cancel(Armed armed) {
        armed.handle().cancel();
    }
}
