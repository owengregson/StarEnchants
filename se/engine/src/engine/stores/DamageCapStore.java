package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player damage-cap state for the Diminish enchants (ADR-0049): {@link #lastTaken} records the final damage of
 * the last committed hit against a player (always, every hit), and {@link #armed} holds a one-shot cap the wearer
 * armed on a DEFENSE proc — "the NEXT attack against you caps at {@code factor} × the last hit". The cap value is
 * computed at ARM time from {@link #lastTaken} (0/no-history arms nothing), so it is a fixed number here.
 *
 * <p>Self-armed, self-benefiting state: cleared wholesale on quit (relogging only discards your own protection,
 * never an opponent's window), so this is a plain {@link PlayerScoped} store, not a retained one.
 */
public final class DamageCapStore implements PlayerScoped {

    /** One armed cap: the absolute damage ceiling, whether the overflow is reflected, and the expiry tick. */
    public record Cap(double value, boolean reflectOverflow, long expiry) {
    }

    private final Map<UUID, Double> lastTaken = new ConcurrentHashMap<>();
    private final Map<UUID, Cap> armed = new ConcurrentHashMap<>();

    /** Record the final committed damage of the latest hit against {@code player} (the basis for a later cap). */
    public void recordLastTaken(UUID player, double damage) {
        if (player != null) {
            lastTaken.put(player, damage);
        }
    }

    /** The last committed damage taken by {@code player}, or {@code 0} if no hit has been recorded. */
    public double lastTaken(UUID player) {
        return lastTaken.getOrDefault(player, 0.0);
    }

    /**
     * Arm a one-shot cap of {@code value} for {@code player}, expiring at {@code nowTicks + durationTicks}. A
     * non-positive value (no last-taken history) or duration is a no-op. Re-arming replaces the pending cap.
     */
    public void arm(UUID player, double value, boolean reflectOverflow, long nowTicks, int durationTicks) {
        if (player == null || value <= 0 || durationTicks <= 0) {
            return;
        }
        armed.put(player, new Cap(value, reflectOverflow, nowTicks + durationTicks));
    }

    /**
     * Consume {@code player}'s armed cap if one is live at {@code nowTicks}: returns it and clears the arm (a
     * one-shot — it caps exactly the next hit), or {@code null} when none is armed or it has elapsed.
     */
    public Cap consumeArmed(UUID player, long nowTicks) {
        Cap cap = armed.remove(player);
        if (cap == null || nowTicks >= cap.expiry()) {
            return null;
        }
        return cap;
    }

    @Override
    public void clear(UUID player) {
        lastTaken.remove(player);
        armed.remove(player);
    }

    /** Forget every player's cap state (call on disable). */
    public void clearAll() {
        lastTaken.clear();
        armed.clear();
    }
}
