package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed teleport block (docs/architecture.md §5.4, § combat-flags): while armed, the player
 * cannot launch an ender pearl (or chorus-teleport). The {@code TELEBLOCK} effect writes it via the {@link
 * engine.sink.Sink} when a hit lands; a launch listener — a SEPARATE Bukkit event from the hit — reads it
 * back and cancels the launch. The store bridges the two decoupled events — on Folia the write thread may
 * differ from the launch-event thread (cf. {@link KnockbackControlStore}).
 */
public final class TeleblockStore implements RetainedStore {

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

    /** Drop {@code player}'s block (a full clear — NOT the relog-preserving quit sweep). */
    public void clear(UUID player) {
        expiry.remove(player);
    }

    /** Drop {@code player}'s block only if it has already elapsed at {@code nowTicks} (the quit sweep). */
    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        Long until = expiry.get(player);
        if (until != null && nowTicks >= until) {
            expiry.remove(player, until); // value-matched: a concurrent re-block survives
        }
    }

    /** Drop every player's elapsed block at {@code nowTicks} (the periodic offline-state sweep). */
    @Override
    public void evictElapsed(long nowTicks) {
        expiry.entrySet().removeIf(e -> nowTicks >= e.getValue());
    }

    /** Drop every block (on disable). */
    public void clearAll() {
        expiry.clear();
    }
}
