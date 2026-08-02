package feature.bless;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import platform.economy.EconomyService;

/**
 * What it costs to run {@code /bless}: a per-player cooldown and an optional charge, both read live off the
 * master config so {@code /se reload} re-tunes them. Split from {@link CleanseService} because the cleanse is a
 * mechanic and this is a policy — an admin {@code /se bless} performs the same cleanse while skipping this
 * entirely.
 *
 * <p>The cooldown is deliberately NOT swept on quit: a landed cooldown that a reconnect could shed would make
 * the whole knob decorative (the {@code RetainedStore} reasoning, in the small). It is TTL-evicting instead —
 * an elapsed entry is dropped on the owner's next attempt, and {@link #forgetElapsed} lets a periodic sweep
 * bound the map for players who never return.
 *
 * <p>Charging is the LAST gate and happens only once every other check has passed, so a refused bless never
 * takes a player's money. With a cost configured but no economy provider installed the bless is refused rather
 * than silently made free — a missing Vault is an operator misconfiguration, not a discount.
 */
public final class BlessGate {

    /** Why a bless was refused, or that it may proceed. */
    public enum Verdict {
        /** Every gate passed; any cost has been charged. */
        ALLOWED,
        /** Still cooling down — see {@link Decision#remainingSeconds()}. */
        COOLING_DOWN,
        /** A cost is configured but no economy provider is installed. */
        NO_ECONOMY,
        /** The player cannot pay {@link Decision#cost()}. */
        CANNOT_AFFORD
    }

    /** A gate outcome plus the numbers a message needs. */
    public record Decision(Verdict verdict, long remainingSeconds, double cost) {

        public boolean allowed() {
            return verdict == Verdict.ALLOWED;
        }
    }

    /** The live knobs, read per attempt so a reload takes effect immediately. */
    public record Settings(int cooldownSeconds, double cost) {
    }

    private final Supplier<Settings> settings;
    private final EconomyService economy;
    private final Map<UUID, Long> readyAtMillis = new ConcurrentHashMap<>();

    public BlessGate(Supplier<Settings> settings, EconomyService economy) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /**
     * Check every gate for {@code player} at {@code nowMillis} and, on {@link Verdict#ALLOWED}, commit both
     * sides — charge the cost and arm the cooldown — so a caller that acts on an ALLOWED decision can never
     * double-spend the window. Nothing is charged or armed on any refusal.
     *
     * <p>A cooldown of {@code 0} means no cooldown, including for a window armed before the knob was turned
     * down: the setting is consulted before the map, so an operator relaxing it on {@code /se reload} frees
     * everyone immediately rather than leaving the last cohort serving out the old window.
     */
    public Decision claim(UUID player, long nowMillis) {
        Settings live = settings.get();
        Long readyAt = live.cooldownSeconds() > 0 ? readyAtMillis.get(player) : null;
        if (readyAt != null) {
            if (nowMillis < readyAt) {
                long remaining = (readyAt - nowMillis + 999L) / 1000L; // round up: "1s left" never shows as 0
                return new Decision(Verdict.COOLING_DOWN, remaining, live.cost());
            }
            readyAtMillis.remove(player, readyAt); // elapsed — shed it rather than let the map grow
        }
        if (live.cost() > 0) {
            if (!economy.present()) {
                return new Decision(Verdict.NO_ECONOMY, 0L, live.cost());
            }
            if (!economy.withdraw(player, live.cost())) {
                return new Decision(Verdict.CANNOT_AFFORD, 0L, live.cost());
            }
        }
        if (live.cooldownSeconds() > 0) {
            readyAtMillis.put(player, nowMillis + live.cooldownSeconds() * 1000L);
        }
        return new Decision(Verdict.ALLOWED, 0L, live.cost());
    }

    /** Drop every already-elapsed entry (periodic sweep — the map only grows via players who never return). */
    public void forgetElapsed(long nowMillis) {
        readyAtMillis.values().removeIf(readyAt -> nowMillis >= readyAt);
    }

    /** Drop every entry (plugin disable). */
    public void clearAll() {
        readyAtMillis.clear();
    }
}
