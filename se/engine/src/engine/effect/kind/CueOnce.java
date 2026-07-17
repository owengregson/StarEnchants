package engine.effect.kind;

import java.util.Arrays;

/**
 * One audible cue per sound per hit (ADR-0066). Distinct activations inside ONE event walk bracket can
 * legitimately author the SAME sound — sibling enchants sharing a likeness cue (Lifesteal + Chain Lifesteal
 * both proc'ing one swing), the same armor enchant worn on several pieces, an ECHO_STRIKE pass re-activating
 * a cooldown-0 ability — and each played its cue, so one swing double-played "the enchant sound" (owner
 * report). The re-hit guard (§3.7) can never see these: they are one event, not two.
 *
 * <p>{@link #claim} is true the first time a sound id is emitted toward a given per-event sink and false for
 * every repeat toward that SAME sink, collapsing the duplicates at the emission seam while a different sound
 * on the same hit, or the same sound on the next hit (next sink), still plays.
 *
 * <p>Thread-confined by construction, exactly like {@code EngineDamage}: every dispatcher builds one sink per
 * event and runs its whole gate walk synchronously on the firing thread, so "the sink changed" IS "a new
 * event began on this thread". The state is one sink reference plus a handful of ints per thread — nothing
 * to tear down, and delivery threads never claim (emission only).
 */
final class CueOnce {

    private static final ThreadLocal<CueOnce> CURRENT = ThreadLocal.withInitial(CueOnce::new);

    private Object sink;
    private int[] played = new int[8];
    private int count;

    private CueOnce() {
    }

    /** Whether {@code soundId} is this event-sink's first emission of that sound — record it if so. */
    static boolean claim(Object sink, int soundId) {
        CueOnce state = CURRENT.get();
        if (state.sink != sink) {
            state.sink = sink; // a new per-event sink opens a new bracket; the old set dies with it
            state.count = 0;
        }
        for (int i = 0; i < state.count; i++) {
            if (state.played[i] == soundId) {
                return false;
            }
        }
        if (state.count == state.played.length) {
            state.played = Arrays.copyOf(state.played, state.count * 2);
        }
        state.played[state.count++] = soundId;
        return true;
    }
}
