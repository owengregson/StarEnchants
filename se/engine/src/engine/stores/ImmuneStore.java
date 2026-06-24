package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed damage immunity by cause (docs/architecture.md §5.4, § combat-flags): while armed, the
 * player ignores hits of a given {@link Type}. The {@code IMMUNE} effect writes it through the per-event
 * {@link engine.sink.Sink}; a damage listener — a SEPARATE Bukkit event from the hit that armed it — reads
 * it back and cancels matching damage. The same store-bridges-two-events shape as {@link KnockbackControlStore}.
 *
 * <p>Concurrent and UUID-keyed for Folia. Each player holds an expiry tick per immunity type (a small fixed
 * array), so independent immunities (e.g. SWORD and PROJECTILE) coexist; {@link Type#ALL} covers every cause.
 * Time is an explicit tick count from the caller (never wall-clock) — deterministic, Folia-correct, testable.
 */
public final class ImmuneStore {

    /** The damage causes an immunity can cover; the ordinal is the wire code passed through the {@code Sink}. */
    public enum Type {
        SWORD, AXE, PROJECTILE, POTION, ALL;

        private static final Type[] VALUES = values();

        /** The type for a wire code (0..4), or {@code null} if out of range. */
        public static Type of(int code) {
            return code >= 0 && code < VALUES.length ? VALUES[code] : null;
        }
    }

    private final Map<UUID, long[]> expiries = new ConcurrentHashMap<>();

    /** Grant {@code player} immunity to {@code type} until {@code nowTicks + ttlTicks}. A non-positive TTL is a no-op. */
    public void immune(UUID player, Type type, long nowTicks, int ttlTicks) {
        if (type == null || ttlTicks <= 0) {
            return;
        }
        long until = nowTicks + ttlTicks;
        expiries.compute(player, (id, prev) -> {
            long[] slots = prev != null ? prev : new long[Type.VALUES.length];
            slots[type.ordinal()] = Math.max(slots[type.ordinal()], until); // extend, never shorten
            return slots;
        });
    }

    /** Whether {@code player} is immune to {@code type} right now — true if that type or {@link Type#ALL} is active. */
    public boolean isImmune(UUID player, Type type, long nowTicks) {
        long[] slots = expiries.get(player);
        if (slots == null || type == null) {
            return false;
        }
        return nowTicks < slots[type.ordinal()] || nowTicks < slots[Type.ALL.ordinal()];
    }

    /** Drop {@code player}'s immunities (on quit). */
    public void clear(UUID player) {
        expiries.remove(player);
    }

    /** Drop every immunity (on disable). */
    public void clearAll() {
        expiries.clear();
    }
}
