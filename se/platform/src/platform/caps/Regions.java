package platform.caps;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The ONE guard for the expected Folia cross-region read fault (ADR-0042). On Folia, touching an
 * entity/block/world owned by another region throws a RuntimeException BY DESIGN — expected, silent;
 * the caller falls back. On Paper/legacy the identical catch could only swallow a REAL bug, so there
 * the full stack is logged at DEBUG/FINE instead of vanishing. Cold callers wrap the read in
 * {@link #read}; hot-path callers (per-activation fact populates) keep their local try/catch — no
 * lambda capture per hit — and report the catch through {@link #swallowed}.
 */
public final class Regions {

    private static final System.Logger LOG = System.getLogger("StarEnchants.Regions");

    // Class-probe default so the guard is right even before Scheduling.init; init overrides from the
    // probed Capabilities, and tests flip it explicitly.
    private static volatile boolean folia = Capabilities.foliaPresent();

    private Regions() {
    }

    /** Install the platform flag (Scheduling.init does this; tests may force either mode). */
    public static void install(boolean foliaPresent) {
        folia = foliaPresent;
    }

    /** Run {@code body}; a cross-region RuntimeException yields {@code fallback}. */
    public static <T> T read(String site, Supplier<T> body, T fallback) {
        Objects.requireNonNull(body, "body");
        try {
            return body.get();
        } catch (RuntimeException fault) {
            swallowed(site, fault);
            return fallback;
        }
    }

    /** Report a guarded cross-region catch: silent on Folia (expected); FINE + full stack on non-Folia. */
    public static void swallowed(String site, RuntimeException fault) {
        if (!folia) {
            LOG.log(System.Logger.Level.DEBUG, "guarded read failed at " + site + " on a non-Folia server", fault);
        }
    }
}
