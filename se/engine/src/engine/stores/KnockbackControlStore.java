package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-victim timed knockback control (docs/architecture.md §5.4, § combat-flags): a multiplier applied to
 * the next knockback an entity takes, with a short TTL. The {@code KNOCKBACK_CONTROL} effect writes it via
 * the {@link engine.sink.Sink} when a hit lands; the knockback event — a SEPARATE Bukkit event from the
 * damage hit, same tick — reads it back and cancels ({@code multiplier <= 0}) or scales. The two events
 * are decoupled, so an inline read-back cannot carry the flag across; this store bridges them.
 *
 * <p>Concurrent, UUID-keyed (Folia: the firing region's thread may differ from the knockback-event
 * thread). TTL-evicting on read; the TTL is normally a couple of ticks, so it self-bounds almost at once.
 * Time is an explicit caller-supplied tick, never wall-clock — deterministic, Folia-correct, testable.
 */
public final class KnockbackControlStore {

    /** A control: the knockback multiplier and the tick it stops applying. */
    private record Control(double multiplier, long expiry) {
    }

    /** Returned by {@link #multiplier} when {@code victim} has no active control. */
    public static final double NONE = Double.NaN;

    private final Map<UUID, Control> byVictim = new ConcurrentHashMap<>();

    /**
     * Scale {@code victim}'s incoming knockback for {@code ttlTicks} ({@code 0} cancels, {@code 0.5} halves,
     * {@code 2} doubles). Non-positive TTL is a no-op; the multiplier is clamped at {@code 0} (a knockback
     * cannot reverse). Last write wins, matching the single-knockback-per-hit model.
     */
    public void control(UUID victim, double multiplier, long nowTicks, int ttlTicks) {
        if (victim == null || ttlTicks <= 0) {
            return;
        }
        byVictim.put(victim, new Control(Math.max(0.0, multiplier), nowTicks + ttlTicks));
    }

    /**
     * @return the active multiplier for {@code victim} at {@code nowTicks}, or {@link #NONE} ({@code NaN})
     *     if none / elapsed. Evicted lazily; the window is half-open {@code [start, expiry)}.
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
