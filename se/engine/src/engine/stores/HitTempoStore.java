package engine.stores;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quickening Fang state (ADR-0071 HIT_TEMPO): the holder's active tempo window, and per-victim
 * "stolen interval" stamps — the span in which a holder's landed hit artificially cleared the
 * victim's i-frames, during which non-participant hits are gated to restore third-party fairness.
 *
 * <p>Self-armed, self-benefiting combat state — swept VOLATILE on quit like the damage cap.
 * {@code engine.stores} is a hot-path package (EngineBoundaryArchTest): UUID-keyed concurrent maps
 * only, no Bukkit or platform imports.
 */
public final class HitTempoStore implements PlayerScoped {

    /** The holder's armed tempo window. {@code model}: 0 = MENTAL (W = max/2), 1 = VANILLA (W = max). */
    public record Window(long expiryTick, int model, double damageFactor) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    // Per-victim stolen intervals, keyed by HOLDER (victim -> holder -> untilTick): each holder's
    // landed tempo hit stamps its OWN interval, so two concurrent Quickening holders never overwrite
    // each other (a single slot would let the newest stamp turn the other holder into a "third
    // party" and cancel their hits at LOWEST — the two-holder finding).
    private final Map<UUID, Map<UUID, Long>> stolen = new ConcurrentHashMap<>();

    /** Arm/refresh the holder's tempo window. {@code damageFactor} is the per-hit multiplier (e.g. 0.333). */
    public void arm(UUID holder, long nowTicks, int durationTicks, int model, double damageFactor) {
        if (holder == null || durationTicks <= 0) {
            return;
        }
        windows.put(holder, new Window(nowTicks + durationTicks, model, damageFactor));
    }

    /** The holder's live window at {@code nowTicks}, or {@code null} (elapsed entries lazily removed). */
    public Window window(UUID holder, long nowTicks) {
        Window w = windows.get(holder);
        if (w == null) {
            return null;
        }
        if (nowTicks >= w.expiryTick()) {
            windows.remove(holder, w);
            return null;
        }
        return w;
    }

    /** Stamp the holder's own stolen interval on {@code victim} after a landed tempo hit (MONITOR side). */
    public void stampStolen(UUID victim, UUID holder, long untilTick) {
        if (victim == null || holder == null) {
            return;
        }
        stolen.computeIfAbsent(victim, v -> new ConcurrentHashMap<>()).put(holder, untilTick);
    }

    /**
     * True iff {@code attacker}'s hit on {@code victim} at {@code nowTicks} must be gated: at least
     * one stolen interval on the victim is live AND the attacker holds NO live interval of their
     * own. Every actively-striking window holder keeps their own cadence; third parties see exactly
     * the i-frames the writes erased. Lazily evicts elapsed stamps + empty victim entries.
     */
    public boolean stolenBlocks(UUID victim, UUID attacker, long nowTicks) {
        Map<UUID, Long> byHolder = stolen.get(victim);
        if (byHolder == null) {
            return false;
        }
        boolean anyLive = false;
        boolean attackerLive = false;
        for (Iterator<Map.Entry<UUID, Long>> it = byHolder.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (nowTicks >= e.getValue()) {
                it.remove();
                continue;
            }
            anyLive = true;
            if (e.getKey().equals(attacker)) {
                attackerLive = true;
            }
        }
        if (byHolder.isEmpty()) {
            stolen.remove(victim, byHolder);
        }
        return anyLive && !attackerLive;
    }

    // Removes the player as HOLDER (windows) AND as VICTIM (stolen); any holder-side stamps they
    // hold on OTHER victims lapse on their own <= max/2 clocks (self-expiring, harmless post-quit).
    @Override
    public void clear(UUID player) {
        windows.remove(player);
        stolen.remove(player);
    }

    /** Forget every player's tempo state (call on disable). */
    public void clearAll() {
        windows.clear();
        stolen.clear();
    }
}
