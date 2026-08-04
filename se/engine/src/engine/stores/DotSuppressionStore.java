package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player windows in which named vanilla damage-over-time causes deal NO damage — the {@code PERIODIC_DAMAGE}
 * {@code replace} contract. An engine burn stands in for the vanilla DoT's damage TICKS while the status effect
 * itself is left untouched and fully visible (Divine Immolation's wither icon survives its own conversion), so
 * the read path CANCELS a covered tick rather than stripping or denying the effect that produced it.
 *
 * <p>MERGE = union the causes, keep the LATER expiry. A second burn must never shorten or narrow a live window:
 * that would restart the first burn's replaced DoT mid-conversion. This is deliberately not
 * {@link DotAmplifyStore}'s replace-whole rule — that one models a re-infection, this one a lease held for as
 * long as anything is still burning.
 *
 * <p>QUIT-VOLATILE, also unlike {@link DotAmplifyStore}: the burn paying for the lease dies with its victim's
 * logout (the pulse chain's liveness gate), so retaining the lease would hand a relogging victim free DoT
 * immunity for the rest of a window nothing is burning them for.
 */
public final class DotSuppressionStore implements PlayerScoped {

    /** The DoT cause bits, shared with {@link DotAmplifyStore} so ONE DamageCause→bit mapping feeds both marks. */
    public static final int CAUSE_WITHER = DotAmplifyStore.CAUSE_WITHER;

    /** @see #CAUSE_WITHER */
    public static final int CAUSE_POISON = DotAmplifyStore.CAUSE_POISON;

    /** One lease: the causes whose damage is cancelled, and the tick it lapses on. */
    private record Window(int causeMask, long expiry) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    /**
     * Cancel {@code player}'s incoming {@code causeMask} damage-over-time ticks for {@code durationTicks}.
     * An empty mask or a non-positive duration is a no-op; anything else unions onto a live window and keeps
     * whichever expiry is later.
     */
    public void suppress(UUID player, int causeMask, long nowTicks, int durationTicks) {
        if (player == null || causeMask == 0 || durationTicks <= 0) {
            return;
        }
        Window armed = new Window(causeMask, nowTicks + durationTicks);
        windows.merge(player, armed, (live, fresh) -> nowTicks >= live.expiry() ? fresh
                : new Window(live.causeMask() | fresh.causeMask(), Math.max(live.expiry(), fresh.expiry())));
    }

    /** Whether {@code causeBit}'s damage is cancelled for {@code player} at {@code nowTicks} (self-evicting). */
    public boolean suppressed(UUID player, long nowTicks, int causeBit) {
        Window window = windows.get(player);
        if (window == null) {
            return false;
        }
        if (nowTicks >= window.expiry()) {
            windows.remove(player, window);
            return false;
        }
        return (window.causeMask() & causeBit) != 0;
    }

    @Override
    public void clear(UUID player) {
        windows.remove(player);
    }
}
