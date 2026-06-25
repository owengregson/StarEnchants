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
 * Drives the §B {@code HELD}/{@code PASSIVE} start/stop lifecycle (§B, ADR-0022). Unlike {@code REPEATING}
 * (timer re-fire), a HELD/PASSIVE source is a <em>maintained buff</em>: applied once on activate (equip/hold),
 * torn down once on deactivate (unequip/swap-away). Computes the transition by DIFFING the currently-worn
 * HELD/PASSIVE abilities against the set last seen, then {@link TriggerDispatch#fireLifecycle} STARTs the
 * arrivals and STOPs the departures. A Cosmic Enchants-style deactivation half.
 *
 * <p><strong>Keyed by STABLE key, not dense id (§5.3).</strong> The per-player "started" set holds compiled
 * stable keys ({@code enchants/strength-aura/3}), so it survives a reload (which reassigns dense ids): the
 * same worn item resolves to the same key, the diff is empty, nothing spuriously re-applies. A level swap is
 * naturally a STOP of the old key + START of the new. List-multiplicity is de-duped to one entry per key.
 *
 * <p><strong>Lifecycle.</strong> {@link #refresh} runs after every worn-state refresh, on the player's thread.
 * {@link #clear} (quit) needs no teardown — the entity is gone and its potion effects vanish; rejoin re-applies.
 * {@link #clearAll} drops everything on disable. The per-player set is a {@link ConcurrentHashMap} (Folia
 * region threads).
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
     * Diff {@code worn} against the player's last-started set and fire the transition (STOP departures, START
     * arrivals). Must run on the player's own thread (equip events are player-owned). No-op when neither
     * lifecycle trigger exists, or the worn state is stale against the live snapshot (re-driven next change).
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

        // keyed by stable key so list-multiplicity de-dups to one entry
        Map<String, Ability> active = new LinkedHashMap<>();
        collect(active, worn, held, snapshot);
        collect(active, worn, passive, snapshot);

        Set<String> previous = started.getOrDefault(id, Set.of());
        List<Ability> stops = new ArrayList<>();
        for (String key : previous) {
            if (!active.containsKey(key)) {
                Ability ability = snapshot.byStableKey(key);
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

    /** Drop a player's tracking on quit — no teardown, the entity is gone. */
    public void clear(UUID player) {
        started.remove(player);
    }

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
