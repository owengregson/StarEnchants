package feature.mask;

import engine.stores.PlayerScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mobs a MOB_TARGET-warded wearer has provoked (ADR-0053 §7): {@code wearer → provoked mob → expiry tick}.
 * A warded player is calm — mobs never acquire them — EXCEPT for a mob they attacked (or that already
 * retaliated). That "unless attacked" exception must persist for the FIGHT, not forever, so a provocation
 * carries a generous fixed TTL that every fresh hit re-extends: an active brawl keeps the mob hostile while a
 * wearer who walks away is calm again once the window lapses.
 *
 * <p>Concurrent + allocation-light: writes land on the attacker's own region thread (the MONITOR damage
 * listener), reads on the targeting mob's region thread ({@code MobTargetGuard}) — both UUID-keyed. Satisfies
 * the {@link PlayerScoped} quit seam so the masks module drops it wholesale on quit, alongside the wards.
 */
public final class MaskProvocationStore implements PlayerScoped {

    /** Provocation lifetime (5 min at 20 tps): long enough to outlast a fight, short enough that idling forgets. */
    static final long TTL_TICKS = 6000L;

    private final Map<UUID, Map<UUID, Long>> provoked = new ConcurrentHashMap<>();

    /** Record that {@code wearer} provoked {@code mob}; the aggro sticks until {@code nowTick + TTL_TICKS}. */
    public void provoke(UUID wearer, UUID mob, long nowTick) {
        provoked.computeIfAbsent(wearer, id -> new ConcurrentHashMap<>()).put(mob, nowTick + TTL_TICKS);
    }

    /** Whether {@code wearer} holds a live provocation against {@code mob} at {@code nowTick} (expired entries pruned). */
    public boolean isProvoked(UUID wearer, UUID mob, long nowTick) {
        Map<UUID, Long> mine = provoked.get(wearer);
        if (mine == null) {
            return false;
        }
        Long expiry = mine.get(mob);
        if (expiry == null) {
            return false;
        }
        if (nowTick >= expiry) {
            mine.remove(mob, expiry); // opportunistic prune on a stale hit — keeps the per-wearer map bounded
            return false;
        }
        return true;
    }

    @Override
    public void clear(UUID player) {
        provoked.remove(player);
    }

    /** Drop every provocation (on disable). */
    public void clearAll() {
        provoked.clear();
    }
}
