package feature.apply;

import java.util.Random;

/** The ONE roll vocabulary for the apply/mint economies. Takes the caller's injected Random so live suites
 *  and the legacy smoke can stub it — the inline ThreadLocalRandom form was unstubbable (the class of bug
 *  the legacy smoke once caught in production). */
public final class Rolls {
    private Rolls() { }

    /** A uniform int in {@code [min, max]} inclusive; {@code min} without consuming a draw when the span is empty. */
    public static int between(Random random, int min, int max) {
        int span = max - min;
        return span <= 0 ? min : min + random.nextInt(span + 1);
    }

    /** Whether a {@code [0,100]} percent chance passes: {@code nextInt(100) < percent} — 0 never fires, 100 always. */
    public static boolean passes(Random random, int percent) {
        return random.nextInt(100) < percent;
    }
}
