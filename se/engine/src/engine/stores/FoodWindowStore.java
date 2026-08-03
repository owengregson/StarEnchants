package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player armed hunger windows by {@link Type}: {@code MODIFY_FOOD} arms one via the
 * {@link engine.sink.Sink}, and the shared {@code FoodLevelChangeEvent} listener — a SEPARATE Bukkit event
 * from the arming activation, on a later tick than the meal that causes it — reads it back to scale a gain
 * or cancel a drain. The store is what bridges the two (the {@link TeleblockStore} shape).
 *
 * <p>The TTL substitutes for an unequip teardown: the engine has no {@code EffectKind.stop()}, so a
 * while-worn window is re-armed by a PASSIVE/REPEATING ability with a TTL at least the repeat period and
 * lapses shortly after re-arming stops. The quit sweep drops it wholesale — self-armed, self-benefiting
 * state, like {@code WARD}.
 */
public final class FoodWindowStore implements PlayerScoped {

    /** The hunger semantics a window covers; the ordinal is the wire code passed through the {@code Sink}. */
    public enum Type {
        SCALE_GAIN, CANCEL_DRAIN;

        private static final Type[] VALUES = values();

        /** The type for a wire code (0..1), or {@code null} if out of range. */
        public static Type of(int code) {
            return code >= 0 && code < VALUES.length ? VALUES[code] : null;
        }
    }

    /** Parallel per-type slots: {@code until[t]} is the absolute expiry tick, {@code factor[t]} its multiplier. */
    private record Slots(long[] until, double[] factor) {
        static Slots fresh() {
            return new Slots(new long[Type.VALUES.length], new double[Type.VALUES.length]);
        }
    }

    private final Map<UUID, Slots> windows = new ConcurrentHashMap<>();

    /**
     * Arm {@code player}'s {@code type} window until {@code nowTicks + ttlTicks}, carrying {@code factor}
     * (unused by CANCEL_DRAIN). A non-positive TTL is a no-op. Re-arming extends, never shortens; the
     * later-expiring arm's factor wins with its window.
     */
    public void arm(UUID player, Type type, long nowTicks, int ttlTicks, double factor) {
        if (player == null || type == null || ttlTicks <= 0) {
            return;
        }
        long until = nowTicks + ttlTicks;
        windows.compute(player, (id, prev) -> {
            Slots slots = prev != null ? prev : Slots.fresh();
            int t = type.ordinal();
            if (until >= slots.until()[t]) { // extend, never shorten (the TeleblockStore merge rule)
                slots.until()[t] = until;
                slots.factor()[t] = factor;
            }
            return slots;
        });
    }

    /** The live SCALE_GAIN multiplier for {@code player} at {@code nowTicks}, or {@code 1} when unarmed. */
    public double gainFactor(UUID player, long nowTicks) {
        Slots slots = windows.get(player);
        int t = Type.SCALE_GAIN.ordinal();
        return slots == null || nowTicks >= slots.until()[t] ? 1.0 : slots.factor()[t];
    }

    /** Whether {@code player} holds a live CANCEL_DRAIN window at {@code nowTicks} (half-open: expiry is free). */
    public boolean cancelsDrain(UUID player, long nowTicks) {
        Slots slots = windows.get(player);
        return slots != null && nowTicks < slots.until()[Type.CANCEL_DRAIN.ordinal()];
    }

    /** Drop {@code player}'s windows (on quit — self-derived state, re-armed by REPEATING within a period). */
    @Override
    public void clear(UUID player) {
        windows.remove(player);
    }

    /** Drop every window (on disable). */
    public void clearAll() {
        windows.clear();
    }
}
