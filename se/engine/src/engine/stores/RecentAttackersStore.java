package engine.stores;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-victim recent-attacker tracking for the gank enchants (ADR-0049: Aegis / Anti Gank): each victim UUID maps
 * to an insertion-ordered {@code attacker -> lastHitTick}, so {@link #distinctCount} answers "how many distinct
 * attackers hit me lately" and {@link #indexOf} answers "which attacker in first-seen order is THIS one" (the
 * gank shield reduces damage from attackers beyond the first N). The window is {@value #WINDOW_TICKS} ticks,
 * evicted lazily on each read/write so the map self-bounds without a timer.
 *
 * <p>First-seen order matters ({@link #indexOf} is 1-based by it), so the inner map is a {@link LinkedHashMap}
 * — an existing attacker's re-hit refreshes its tick but keeps its position; only a NEW or re-appeared (expired)
 * attacker lands at the end. Every access synchronizes on that inner map: combat events for one victim fire on
 * the victim's own region thread on Folia, but the concurrent outer map plus per-victim locking keeps it correct
 * even if two regions ever touch one victim's entry.
 */
public final class RecentAttackersStore implements PlayerScoped {

    /** How far back a hit still counts toward the gank window, in ticks (10s). */
    private static final int WINDOW_TICKS = 200;

    private final Map<UUID, Map<UUID, Long>> byVictim = new ConcurrentHashMap<>();

    /** Record that {@code attacker} hit {@code victim} at {@code nowTicks}, refreshing an existing attacker in place. */
    public void record(UUID victim, UUID attacker, long nowTicks) {
        if (victim == null || attacker == null) {
            return;
        }
        Map<UUID, Long> attackers = byVictim.computeIfAbsent(victim, k -> new LinkedHashMap<>());
        synchronized (attackers) {
            evict(attackers, nowTicks);
            attackers.put(attacker, nowTicks); // LinkedHashMap: re-put keeps the first-seen position
        }
    }

    /** Distinct attackers that hit {@code victim} within the window at {@code nowTicks}. */
    public int distinctCount(UUID victim, long nowTicks) {
        Map<UUID, Long> attackers = byVictim.get(victim);
        if (attackers == null) {
            return 0;
        }
        synchronized (attackers) {
            evict(attackers, nowTicks);
            return attackers.size();
        }
    }

    /**
     * The 1-based first-seen order of {@code attacker} among {@code victim}'s live attackers ({@code 1} = the
     * first still-active attacker), or {@code 0} if it has no live hit in the window.
     */
    public int indexOf(UUID victim, UUID attacker, long nowTicks) {
        if (attacker == null) {
            return 0;
        }
        Map<UUID, Long> attackers = byVictim.get(victim);
        if (attackers == null) {
            return 0;
        }
        synchronized (attackers) {
            evict(attackers, nowTicks);
            int index = 1;
            for (UUID id : attackers.keySet()) {
                if (id.equals(attacker)) {
                    return index;
                }
                index++;
            }
            return 0;
        }
    }

    /** Drop entries whose last hit is at or beyond the window (half-open, like the other timed stores). */
    private static void evict(Map<UUID, Long> attackers, long nowTicks) {
        Iterator<Map.Entry<UUID, Long>> it = attackers.entrySet().iterator();
        while (it.hasNext()) {
            if (nowTicks - it.next().getValue() >= WINDOW_TICKS) {
                it.remove();
            }
        }
    }

    @Override
    public void clear(UUID player) {
        byVictim.remove(player);
    }

    /** Forget every victim's attacker history (call on disable). */
    public void clearAll() {
        byVictim.clear();
    }
}
