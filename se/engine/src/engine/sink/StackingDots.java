package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-VICTIM decay-stack counter behind {@code STACKING_DOT}. Static and era-agnostic like
 * {@link FrozenTargets} / {@link OwnerZones}, and torn down at disable through the same registry stop.
 *
 * <p>One counter per victim, deliberately NOT per (victim, attacker) pair: two players standing someone in
 * two overlapping fields ramp the SAME ladder, so a crowded field kills at the rate one field would. That is
 * the shape the decay ladder was measured with, and splitting it would silently double a gank's output.
 *
 * <p>Stacks lapse on their own window rather than being cleared when the victim steps out, which is what
 * makes the ladder feel like rot: stepping off the ground for a moment pauses the ramp, and stepping back
 * within the window resumes it where it stood instead of restarting at one.
 */
public final class StackingDots {

    /** {@code count} stacks, valid until {@code expiryTicks}. */
    private record Stack(int count, long expiryTicks) {
    }

    private static final Map<UUID, Stack> STACKS = new ConcurrentHashMap<>();

    private StackingDots() {
    }

    /**
     * Add one stack for {@code victim} and return the new count (never above {@code cap}, never below 1). A
     * lapsed window restarts at one — the ladder has to be re-climbed once rot has had time to fade.
     */
    public static int bump(UUID victim, int cap, int windowTicks, long nowTicks) {
        int ceiling = Math.max(1, cap);
        long expiry = nowTicks + Math.max(1, windowTicks);
        Stack next = STACKS.compute(victim, (id, live) -> {
            int count = live == null || live.expiryTicks() <= nowTicks ? 1 : Math.min(ceiling, live.count() + 1);
            return new Stack(count, expiry);
        });
        return next.count();
    }

    /** The victim's live stack count, or {@code 0} once the window has lapsed. Never mutates the ladder. */
    public static int stacks(UUID victim, long nowTicks) {
        Stack live = STACKS.get(victim);
        return live == null || live.expiryTicks() <= nowTicks ? 0 : live.count();
    }

    /** Drop a victim's ladder outright — their death, or a quit sweep. */
    public static void clear(UUID victim) {
        STACKS.remove(victim);
    }

    /** Drop every ladder (plugin disable). */
    public static void clearAll() {
        STACKS.clear();
    }
}
