package platform.caps;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

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

    /**
     * Whether {@code entity} is owned by the region executing THIS thread — the safe pre-check before handing it
     * to an API that dereferences it on this thread (e.g. as a damage source: vanilla resolves the damager's
     * handle, which throws for a cross-region entity on Folia). On a single-region server (Paper/legacy) the one
     * main thread owns everything, so {@code true}. Callable from any thread — the Folia ownership probe never
     * itself dereferences the entity's state. A {@code null} entity, or an unverifiable read, is {@code false}
     * (degrade to the safe path). This is the correct guard where {@link #read} on {@code isValid()} is NOT:
     * {@code isValid()} reads a cached field without the region thread-check, so it never faults cross-region.
     */
    public static boolean ownedByCurrentRegion(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!folia) {
            return true;
        }
        try {
            // Reflective (the paper-api floor predates the method); this is a cold path — the paced release, not
            // per-hit — so a resolve per call needs no cached-Method static (which the G2-c gate would flag).
            Method probe = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            return Boolean.TRUE.equals(probe.invoke(null, entity));
        } catch (ReflectiveOperationException | RuntimeException unverifiable) {
            return false;
        }
    }
}
