package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-(entity, potion-type) AMPLIFIER reductions — {@code POTION_AMP_REDUCE}'s live windows, plus the
 * arithmetic that decides what a capped effect looks like. Where {@link LockedPotions} denies a type
 * outright, this one leaves it on at a lower tier, which is the whole reason it exists: the packs that
 * Mortal Coil is aimed at grant HEALTH_BOOST well above the reduction, so a strip would take every bonus
 * heart at the moment its holder is being hit instead of the authored few.
 *
 * <p>Static + era-agnostic, keyed by the version-stable {@link org.bukkit.potion.PotionEffectType#getName()}
 * for {@link LockedPotions}' reason; wall-clock expiry, self-evicting. The registry answers exactly one
 * question — is a reduction already running on this type? — because a second one must NOT compound: the
 * consumer's own {@code cooldown-per-victim} already spans its window, so an overlap can only come from a
 * second attacker, and two subtractions on one max-health pool would take multiples of the authored hearts.
 * The incumbent holds, which errs toward the victim.
 */
public final class ReducedPotions {

    private ReducedPotions() {
    }

    /** {@link #reduced} sentinel: the reduction met or exceeded the source, so the type is denied outright. */
    public static final int DENIED = -1;

    private static final Map<UUID, Map<String, Long>> WINDOWS = new ConcurrentHashMap<>();

    /**
     * The amplifier a source measured at {@code sourceAmplifier} keeps under a reduction of {@code amount}
     * LEVELS — the contract's {@code source − N}, and the ceiling every re-application during the window is
     * held to. Levels are 1-based and Bukkit amplifiers 0-based, but they differ by a constant, so a
     * DIFFERENCE is the same number in either unit and the authored amount subtracts unchanged.
     * {@link #DENIED} once nothing is left: HEALTH_BOOST I less two levels is not level −1, it is gone.
     */
    public static int reduced(int sourceAmplifier, int amount) {
        int left = sourceAmplifier - amount;
        return left < 0 ? DENIED : left;
    }

    /**
     * The duration to restore the source with when the window closes and the effect is NOT live (it was
     * denied outright, or lapsed): what was left of it at the arm, less the window it sat out. {@code 0} =
     * it would have expired anyway, so nothing is given back. A negative captured duration is the 1.19.4+
     * infinite marker and rides through verbatim — subtracting from it would invent a finite buff.
     */
    public static int restoreDuration(int capturedDuration, int windowTicks) {
        if (capturedDuration < 0) {
            return capturedDuration;
        }
        return Math.max(0, capturedDuration - windowTicks);
    }

    /**
     * Claim the reduction window on {@code (entity, potionName)} for {@code durationMs}, or answer
     * {@code false} because one is already running — the caller then does nothing at all, leaving the
     * incumbent's cap and its restore untouched. Atomic per key, so two same-tick procs from two attackers
     * cannot both claim it.
     */
    public static boolean arm(UUID entity, String potionName, long durationMs) {
        if (entity == null || potionName == null || durationMs <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean[] claimed = {false};
        WINDOWS.computeIfAbsent(entity, k -> new ConcurrentHashMap<>()).compute(potionName, (key, live) -> {
            if (live != null && live > now) {
                return live;
            }
            claimed[0] = true;
            return now + durationMs;
        });
        return claimed[0];
    }

    /** Drop the window at its close, so the next proc may claim it. */
    public static void release(UUID entity, String potionName) {
        if (entity == null || potionName == null) {
            return;
        }
        Map<String, Long> byType = WINDOWS.get(entity);
        if (byType != null) {
            byType.remove(potionName);
            if (byType.isEmpty()) {
                WINDOWS.remove(entity, byType);
            }
        }
    }

    /** Forget all windows (disable) — a stale claim must not outlive the tasks that would have released it. */
    public static void clearAll() {
        WINDOWS.clear();
    }
}
