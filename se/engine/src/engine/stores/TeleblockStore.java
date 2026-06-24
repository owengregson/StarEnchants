package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed teleport block (docs/architecture.md §5.4, § combat-flags): while armed, the player
 * cannot launch an ender pearl (or chorus-teleport). The {@code TELEBLOCK} effect writes it through the
 * per-event {@link engine.sink.Sink} when a hit lands; a teleport/projectile-launch listener — a SEPARATE
 * Bukkit event from the hit — reads it back and cancels the launch. The two events are decoupled in time,
 * so this short-lived store bridges them (the same shape as {@link KnockbackControlStore}).
 *
 * <p>Concurrent and UUID-keyed for Folia (the write thread may differ from the launch-event thread), and
 * TTL-evicting: an elapsed block is dropped lazily on the next {@link #isBlocked} read. Time is an explicit
 * tick count supplied by the caller (never wall-clock) — deterministic, Folia-correct, unit-testable.
 */
public final class TeleblockStore {

    private final Map<UUID, Long> expiry = new ConcurrentHashMap<>();

    /** Block {@code player} from teleporting until {@code nowTicks + ttlTicks}. A non-positive TTL is a no-op. */
    public void block(UUID player, long nowTicks, int ttlTicks) {
        if (ttlTicks <= 0) {
            return;
        }
        expiry.merge(player, nowTicks + ttlTicks, Math::max); // extend, never shorten, an existing block
    }

    /** Whether {@code player} is currently teleport-blocked; lazily evicts an elapsed entry. */
    public boolean isBlocked(UUID player, long nowTicks) {
        Long until = expiry.get(player);
        if (until == null) {
            return false;
        }
        if (nowTicks >= until) {
            expiry.remove(player, until);
            return false;
        }
        return true;
    }

    /** Drop {@code player}'s block (on quit). */
    public void clear(UUID player) {
        expiry.remove(player);
    }

    /** Drop every block (on disable). */
    public void clearAll() {
        expiry.clear();
    }
}
