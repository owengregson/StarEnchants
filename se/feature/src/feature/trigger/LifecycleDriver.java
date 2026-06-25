package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Drives the §B {@code HELD}/{@code PASSIVE} start/stop lifecycle (docs/v3-directives.md §B, ADR-0022) — the
 * deactivation half a Cosmic Enchants-style plugin has and this engine otherwise lacks. Where {@code REPEATING} re-fires on a timer, a
 * HELD/PASSIVE source is a <em>maintained buff</em>: its effects apply once when the source becomes active
 * (equip/hold) and are torn down once when it becomes inactive (unequip/swap-away). This driver computes that
 * transition by DIFFING the player's currently-worn HELD/PASSIVE abilities against the set it last saw, then
 * asking {@link TriggerDispatch#fireLifecycle} to START the newly-worn and STOP the newly-removed.
 *
 * <p><strong>Keyed by STABLE key, not dense id (§5.3).</strong> The "started" set per player is the set of
 * compiled stable keys ({@code enchants/strength-aura/3}), never dense ids — so it survives a {@code /se reload}
 * (which reassigns every dense id): the same physically-worn item resolves to the same key, the diff comes up
 * empty, and nothing spuriously re-applies. A level swap (strength/2 → strength/3) is naturally a STOP of the
 * old key and a START of the new. List-multiplicity (the same enchant on two worn pieces) is de-duped to one
 * entry per key.
 *
 * <p><strong>Lifecycle.</strong> {@link #refresh} is called by {@code EquipListener} after every worn-state
 * refresh, on the player's own thread. {@link #clear} drops a player's tracking on quit — no teardown is needed
 * there (the entity is gone and its potion effects vanish; on rejoin {@code refresh} re-applies). {@link
 * #clearAll} drops everything on disable. The per-player set is a {@link ConcurrentHashMap} for the same reason
 * the other stores are (Folia region threads).
 */
public final class LifecycleDriver {

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final int held;    // -1 if HELD is absent from the vocabulary
    private final int passive; // -1 if PASSIVE is absent from the vocabulary
    private final Map<UUID, Set<String>> started = new ConcurrentHashMap<>();

    public LifecycleDriver(TriggerDispatch dispatch, ContentHolder content, int held, int passive) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.held = held;
        this.passive = passive;
    }

    /**
     * Diff {@code worn}'s HELD/PASSIVE abilities against what {@code player} last had started, and fire the
     * transition: STOP every source that left, START every source that arrived. Must be called on the player's
     * own thread (the equip events are player-owned). A no-op when neither lifecycle trigger exists or the worn
     * state is stale against the live snapshot (it will be re-driven on the next equip change / reload).
     */
    public void refresh(Player player, WornState worn) {
        UUID id = player.getUniqueId();
        if ((held < 0 && passive < 0) || worn == null) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        if (worn.gen() != snapshot.generation()) {
            return; // worn ids index a different snapshot — skip; the reload re-resolve drives a fresh diff
        }

        // The currently-worn HELD/PASSIVE abilities, keyed by stable key (de-dups list-multiplicity).
        Map<String, Ability> active = new LinkedHashMap<>();
        collect(active, worn, held, snapshot);
        collect(active, worn, passive, snapshot);

        Set<String> previous = started.getOrDefault(id, Set.of());
        List<Ability> stops = new ArrayList<>();
        for (String key : previous) {
            if (!active.containsKey(key)) {
                Ability ability = snapshot.byStableKey(key); // resolve the now-unworn ability for teardown
                if (ability != null) {
                    stops.add(ability);
                }
            }
        }
        List<Ability> starts = new ArrayList<>();
        for (Map.Entry<String, Ability> entry : active.entrySet()) {
            if (!previous.contains(entry.getKey())) {
                starts.add(entry.getValue());
            }
        }

        dispatch.fireLifecycle(player, stops, starts);
        started.put(id, new HashSet<>(active.keySet()));
    }

    /** Drop {@code player}'s lifecycle tracking (call on quit) — no teardown, the entity is gone. */
    public void clear(UUID player) {
        started.remove(player);
    }

    /** Forget every player's lifecycle tracking (call on disable). */
    public void clearAll() {
        started.clear();
    }

    private void collect(Map<String, Ability> into, WornState worn, int triggerId, Snapshot snapshot) {
        if (triggerId < 0) {
            return;
        }
        Ability[] abilities = snapshot.abilities();
        for (int abilityId : worn.byTrigger(triggerId)) {
            if (abilityId < 0 || abilityId >= abilities.length) {
                continue;
            }
            String key = snapshot.stableKeys().keyOf(abilityId);
            if (key != null) {
                into.putIfAbsent(key, abilities[abilityId]);
            }
        }
    }
}
