package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed ward flags by {@link Type}, each carrying an optional scalar amount (ADR-0053 §7):
 * the {@code WARD} effect arms it via the {@link engine.sink.Sink}; a feature guard — a SEPARATE Bukkit
 * event from the arming activation — reads it back (mob-target calm, /invsee shielding, /near hiding,
 * splash-heal boost). The store bridges the two decoupled events (cf. {@link TeleblockStore}).
 *
 * <p>Each player holds one expiry tick + amount per type (small fixed arrays, the {@link ImmuneStore}
 * layout), so independent wards coexist. Content re-arms on REPEATING, so entries self-heal and never
 * outlive the helmet; the quit sweep drops them wholesale (self-derived state, like {@code IMMUNE}).
 */
public final class WardStore implements PlayerScoped {

    /** The ward semantics a flag can cover; the ordinal is the wire code passed through the {@code Sink}. */
    public enum Type {
        MOB_TARGET, INVSEE, NEAR, SPLASH_HEAL;

        private static final Type[] VALUES = values();

        /** The type for a wire code (0..3), or {@code null} if out of range. */
        public static Type of(int code) {
            return code >= 0 && code < VALUES.length ? VALUES[code] : null;
        }
    }

    /** Parallel per-type slots: {@code until[t]} is the absolute expiry tick, {@code amount[t]} its scalar. */
    private record Slots(long[] until, double[] amount) {
        static Slots fresh() {
            return new Slots(new long[Type.VALUES.length], new double[Type.VALUES.length]);
        }
    }

    private final Map<UUID, Slots> wards = new ConcurrentHashMap<>();

    /**
     * Ward {@code player} for {@code type} until {@code nowTicks + ttlTicks}, carrying {@code amount}
     * (0 for the flag-only types). A non-positive TTL is a no-op. Re-arming extends, never shortens;
     * the later-expiring arm's amount wins with its window.
     */
    public void arm(UUID player, Type type, long nowTicks, int ttlTicks, double amount) {
        if (type == null || ttlTicks <= 0) {
            return;
        }
        long until = nowTicks + ttlTicks;
        wards.compute(player, (id, prev) -> {
            Slots slots = prev != null ? prev : Slots.fresh();
            int t = type.ordinal();
            if (until >= slots.until()[t]) { // extend, never shorten (the TeleblockStore merge rule)
                slots.until()[t] = until;
                slots.amount()[t] = amount;
            }
            return slots;
        });
    }

    /** Whether {@code player} holds a live {@code type} ward at {@code nowTicks} (half-open: the expiry tick is free). */
    public boolean isWarded(UUID player, Type type, long nowTicks) {
        Slots slots = wards.get(player);
        return slots != null && type != null && nowTicks < slots.until()[type.ordinal()];
    }

    /** The live ward's amount for {@code player}/{@code type} at {@code nowTicks}, or {@code 0} when unwarded. */
    public double amount(UUID player, Type type, long nowTicks) {
        Slots slots = wards.get(player);
        if (slots == null || type == null || nowTicks >= slots.until()[type.ordinal()]) {
            return 0.0;
        }
        return slots.amount()[type.ordinal()];
    }

    /** Drop {@code player}'s wards (on quit — self-derived state, re-armed by REPEATING within a period). */
    @Override
    public void clear(UUID player) {
        wards.remove(player);
    }

    /** Drop every ward (on disable). */
    public void clearAll() {
        wards.clear();
    }
}
