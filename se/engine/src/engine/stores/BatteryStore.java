package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supernova Core state (ADR-0071 BATTERY): armed on USE; banks a fraction of each landed hit
 * TAKEN (up to {@code maxHits}); the next landed hit DEALT spends the whole core — even at zero
 * banked (owner-ruled). No time expiry by design (the owner spec gives none); dies on
 * spend/death/quit.
 *
 * <p>Self-armed, self-benefiting combat state — swept VOLATILE on quit. Hot-path package rules
 * apply (concurrent maps only).
 */
public final class BatteryStore implements PlayerScoped {

    /** One armed core. {@code hitsLeft} counts remaining bankable incoming hits; {@code banked} is health-space. */
    public record Core(double bankFraction, int hitsLeft, double banked) {
    }

    private final Map<UUID, Core> cores = new ConcurrentHashMap<>();

    /** Arm/replace the holder's core (re-activation resets any residue; gate-6 CD paces this). */
    public void arm(UUID holder, double bankFraction, int maxHits) {
        if (holder == null || maxHits <= 0) {
            return;
        }
        cores.put(holder, new Core(bankFraction, maxHits, 0.0));
    }

    public boolean armed(UUID holder) {
        return cores.containsKey(holder);
    }

    /**
     * Bank {@code finalDamage × bankFraction} if hits remain; returns the UPDATED core — the
     * listener renders the {STORED}/{HITS} actionbar from this atomic result (a separate read would
     * race a concurrent spend). Returns {@code null} past {@code maxHits} or unarmed (no bank
     * happened). The core is kept in place when full so the discharge still spends it.
     */
    public Core bank(UUID holder, double finalDamage) {
        if (holder == null) {
            return null;
        }
        Core[] out = new Core[1];
        cores.computeIfPresent(holder, (id, core) -> {
            if (core.hitsLeft() <= 0) {
                return core; // full: hold the charge; out stays null (no bank happened)
            }
            Core updated = new Core(core.bankFraction(), core.hitsLeft() - 1,
                    core.banked() + finalDamage * core.bankFraction());
            out[0] = updated;
            return updated;
        });
        return out[0];
    }

    /** The banked total without consuming (the dispatch's optimistic fold read). */
    public double peek(UUID holder) {
        Core core = cores.get(holder);
        return core == null ? 0.0 : core.banked();
    }

    /** Spend the core: returns banked and removes the state (the MONITOR landed-hit commit). */
    public double spend(UUID holder) {
        Core core = cores.remove(holder);
        return core == null ? 0.0 : core.banked();
    }

    @Override
    public void clear(UUID player) {
        cores.remove(player);
    }

    /** Forget every player's core (call on disable). */
    public void clearAll() {
        cores.clear();
    }
}
