package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Fires {@code EQUIP_CHANGE} off the worn-ability diff each {@code EquipListener} refresh produces: abilities
 * that arrived walk with {@code %equipchange% == "EQUIP"}, ones that left with {@code "UNEQUIP"}. Driven from
 * the END of {@code EquipListener.refresh}, so it runs on the player's own region thread against the NEW state.
 *
 * <p>Keyed by STABLE key like {@link LifecycleDriver}, for the same reason: the set has to survive a reload,
 * which reassigns dense ids. That is also what lets the leaving piece fire — its ability is gone from the worn
 * state, so its dense id is re-resolved from the snapshot by key, and {@link TriggerDispatch#fireEquipChange}
 * runs it as an explicit candidate.
 *
 * <p>Unlike HELD/PASSIVE this is a fired trigger, not a maintained buff: both directions run the full gate
 * sequence, so an authored chance/cooldown/condition applies to a transition exactly as it would to a hit.
 */
public final class EquipChangeDriver {

    /** The {@code %equipchange%} vocabulary — spelled once here so the driver and its tests cannot drift. */
    public static final String EQUIP = "EQUIP";

    public static final String UNEQUIP = "UNEQUIP";

    private final TriggerDispatch dispatch;
    private final ContentHolder content;
    private final WornStateStore worn;
    private final Map<UUID, Set<String>> equipped = new ConcurrentHashMap<>();

    public EquipChangeDriver(TriggerDispatch dispatch, ContentHolder content, WornStateStore worn) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.content = Objects.requireNonNull(content, "content");
        this.worn = Objects.requireNonNull(worn, "worn");
    }

    /**
     * Diff {@code player}'s EQUIP_CHANGE abilities against the last-seen set and fire both directions. Must run
     * on the player's own thread. A stale worn state (mid-reload) is skipped — the reload's own re-resolve drives
     * a fresh diff, and firing against a foreign generation would run the wrong abilities.
     */
    public void refresh(Player player) {
        if (dispatch.equipChange < 0) {
            return;
        }
        WornState state = worn.get(player.getUniqueId());
        Snapshot snapshot = content.snapshot();
        if (state == null || state.gen() != snapshot.generation()) {
            return;
        }
        // Key → dense id, so multiplicity de-dups to one entry and the EQUIP side needs no id round-trip.
        Map<String, Integer> current = new LinkedHashMap<>();
        for (int abilityId : state.byTrigger(dispatch.equipChange)) {
            String key = snapshot.stableKeys().keyOf(abilityId);
            if (key != null) {
                current.putIfAbsent(key, abilityId);
            }
        }
        Set<String> previous = equipped.getOrDefault(player.getUniqueId(), Set.of());
        // Stamp BEFORE dispatching: an effect that mutates equipment re-enters refresh, and an un-stamped
        // set would let the same transition fire twice.
        equipped.put(player.getUniqueId(), new LinkedHashSet<>(current.keySet()));

        List<Integer> removed = new ArrayList<>();
        for (String key : previous) {
            if (!current.containsKey(key)) {
                // The departed ability is no longer in byTrigger, so its id comes back through the key it kept.
                Ability ability = snapshot.byStableKey(key);
                if (ability != null) {
                    removed.add(ability.id());
                }
            }
        }
        List<Integer> added = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            if (!previous.contains(entry.getKey())) {
                added.add(entry.getValue());
            }
        }
        // UNEQUIP first, mirroring the HELD/PASSIVE STOP-before-START order: a level swap sheds the old piece's
        // ability before the new one's runs, so a paired arm/disarm cannot land inverted.
        dispatch.fireEquipChange(player, ids(removed), UNEQUIP);
        dispatch.fireEquipChange(player, ids(added), EQUIP);
    }

    /** Drop a player's tracking on quit — nothing to fire, the gear left with the entity. */
    public void clear(UUID player) {
        equipped.remove(player);
    }

    public void clearAll() {
        equipped.clear();
    }

    private static int[] ids(List<Integer> from) {
        int[] out = new int[from.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = from.get(i);
        }
        return out;
    }
}
