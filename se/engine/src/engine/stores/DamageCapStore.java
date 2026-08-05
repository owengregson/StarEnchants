package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player damage-cap state for the Diminish enchants (ADR-0049): {@link #armed} holds a one-shot cap the
 * wearer opened on a DEFENSE proc — "the NEXT attack against you caps at {@code factor} &times; the damage of
 * the hit that armed this".
 *
 * <p>That basis is why the arm is two-step (R-QC19). {@code DAMAGE_CAP} runs INSIDE the defence walk, where
 * the arming hit's damage is still being folded, so an arm cannot price itself: it records a {@link Pending}
 * factor here and the combat dispatch calls {@link #price} with the committed figure at the fold commit,
 * below every walk. Pricing at the arm (against the previous hit's committed damage, as this store used to)
 * was a one-hit lag that made a Vengeful Diminish's advertised "half of the hit that armed it" mean half of
 * some earlier, unrelated swing. A pending arm with no committed hit against its holder has no arming hit to
 * price off and never materialises; it is dropped by the next {@link #price}, {@link #clear} or expiry.
 *
 * <p>Self-armed, self-benefiting state: cleared wholesale on quit (relogging only discards your own protection,
 * never an opponent's window), so this is a plain {@link PlayerScoped} store, not a retained one.
 */
public final class DamageCapStore implements PlayerScoped {

    /** One armed cap: the absolute damage ceiling, whether the overflow is reflected, and the expiry tick. */
    public record Cap(double value, boolean reflectOverflow, long expiry) {
    }

    /** An arm awaiting its price: the factor to apply to the arming hit, plus the window it will open. */
    private record Pending(double factor, boolean reflectOverflow, long expiry, String feedback) {
    }

    /** What {@link #price} materialised: the cap's value and the line announcing it ({@code ""} = silent). */
    public record Priced(double value, String feedback) {
    }

    private final Map<UUID, Cap> armed = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Record an arm for {@code player} whose ceiling is {@code factor} &times; the damage of the hit currently
     * being folded, expiring {@code durationTicks} after {@code nowTicks}. Priced (and only then armed) by
     * {@link #price}. A non-positive factor or duration is a no-op; re-arming replaces the pending arm, so the
     * last proc of a walk is the one that opens the window — the same last-arm-wins rule {@link #arm} has.
     */
    public void armPending(UUID player, double factor, boolean reflectOverflow, long nowTicks, int durationTicks,
                           String feedback) {
        if (player == null || factor <= 0 || durationTicks <= 0) {
            return;
        }
        pending.put(player, new Pending(factor, reflectOverflow, nowTicks + durationTicks,
                feedback == null ? "" : feedback));
    }

    /**
     * Price {@code player}'s pending arm against {@code committed} — the damage this event actually committed
     * against them — and open its window. Returns what was armed so the caller can announce it at the figure
     * it really carries, or {@code null} when nothing was pending, the window had already elapsed, or the
     * arming hit committed nothing (a 0-damage hit prices a 0 cap, which arms nothing).
     *
     * <p>Called once per committed hit against a player, below every walk. It also clears a stale pending arm
     * from some earlier event, so nothing accumulates for a player who keeps taking hits.
     */
    public Priced price(UUID player, double committed, long nowTicks) {
        Pending arm = pending.remove(player);
        if (arm == null || nowTicks >= arm.expiry()) {
            return null;
        }
        double value = committed * arm.factor();
        if (value <= 0) {
            return null;
        }
        armed.put(player, new Cap(value, arm.reflectOverflow(), arm.expiry()));
        return new Priced(value, arm.feedback());
    }

    /**
     * Arm a one-shot cap of {@code value} for {@code player}, expiring at {@code nowTicks + durationTicks} — the
     * absolute-value arm, for a caller that already knows the ceiling. A non-positive value or duration is a
     * no-op. Re-arming replaces the pending cap.
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
        armed.remove(player);
        pending.remove(player);
    }

    /** Forget every player's cap state (call on disable). */
    public void clearAll() {
        armed.clear();
        pending.clear();
    }
}
