package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-victim timed knockback control (docs/architecture.md §5.4, § combat-flags): a multiplier applied
 * to the next knockback an entity takes, expiring after a short TTL. The {@code KNOCKBACK_CONTROL}
 * effect writes it through the per-event {@link engine.sink.Sink} when a hit lands (a DEFENSE proc on
 * the victim, or an ATTACK proc targeting the victim); the server's knockback event — a SEPARATE Bukkit
 * event from the damage hit, fired the same tick — reads it back and cancels ({@code multiplier <= 0})
 * or scales the applied knockback. The two events are decoupled in time, so an inline read-back like
 * {@code IGNORE_ARMOR} cannot carry the flag across; this short-lived store bridges them.
 *
 * <p>Concurrent and UUID-keyed for Folia (the write thread — the firing region — may differ from the
 * knockback-event thread), and TTL-evicting: an elapsed control is dropped lazily on the next
 * {@link #multiplier} read, so the map stays bounded without a sweeper. Cleared on quit ({@link #clear})
 * and on disable ({@link #clearAll}); the TTL is normally only a couple of ticks, so it self-bounds
 * almost immediately regardless.
 *
 * <p>Time is an explicit tick count supplied by the caller (never wall-clock) — deterministic,
 * Folia-correct, and unit-testable without a server.
 */
public final class KnockbackControlStore {

    /** A control: the knockback multiplier and the tick it stops applying. */
    private record Control(double multiplier, long expiry) {
    }

    /** Returned by {@link #multiplier} when {@code victim} has no active control. */
    public static final double NONE = Double.NaN;

    private final Map<UUID, Control> byVictim = new ConcurrentHashMap<>();

    /**
     * Control {@code victim}'s incoming knockback for {@code ttlTicks}: the next knockback (within the TTL)
     * is multiplied by {@code multiplier} ({@code 0} cancels it, {@code 0.5} halves it, {@code 2} doubles
     * it). A non-positive TTL is a no-op. The multiplier is clamped at {@code 0} (a negative is meaningless
     * — a knockback cannot reverse). The most recent write wins (last DEFENSE/ATTACK proc this hit decides),
     * matching the single-knockback-per-hit model.
     */
    public void control(UUID victim, double multiplier, long nowTicks, int ttlTicks) {
        if (victim == null || ttlTicks <= 0) {
            return;
        }
        byVictim.put(victim, new Control(Math.max(0.0, multiplier), nowTicks + ttlTicks));
    }

    /**
     * @return the active knockback multiplier for {@code victim} at {@code nowTicks}, or {@link #NONE}
     *     ({@code NaN}) if there is none / it has elapsed. An elapsed control is evicted lazily; the expiry
     *     tick itself counts as elapsed (the control covers {@code [start, expiry)}).
     */
    public double multiplier(UUID victim, long nowTicks) {
        Control control = byVictim.get(victim);
        if (control == null) {
            return NONE;
        }
        if (nowTicks >= control.expiry()) {
            byVictim.remove(victim, control); // lazy eviction of an elapsed control
            return NONE;
        }
        return control.multiplier();
    }

    /** Forget any control for one entity (call on quit). */
    public void clear(UUID victim) {
        byVictim.remove(victim);
    }

    /** Forget every control for every entity (call on disable). */
    public void clearAll() {
        byVictim.clear();
    }
}
