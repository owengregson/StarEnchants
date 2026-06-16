package platform.protect;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

/**
 * The composed protection gate the engine consults (docs/architecture.md §2, §3.3 gate 2). ANDs every
 * registered {@link ProtectionProvider}: an action is allowed iff <em>all</em> providers allow it
 * (deny is authoritative). With no providers it allows everything — a server with no land plugin has
 * nothing to protect. A provider that throws is treated as "allow" and logged once, so a buggy bridge
 * degrades to permissive rather than blocking all enchant activity.
 *
 * <p>Pure and Bukkit-light: it holds an immutable provider list and calls them, nothing more. There is
 * deliberately NO per-tick cache — the providers list is usually tiny (often empty) and adding a
 * tick-scoped cache here was a correctness liability (a stalled global tick froze it stale) for a perf
 * win that belongs in a profiled hot-path pass, not the protection gate. Each provider is invoked on
 * the firing region's thread with the firing {@link Location} (see {@link ProtectionProvider}).
 */
public final class ProtectionService {

    private final List<ProtectionProvider> providers;
    private final System.Logger log = System.getLogger("StarEnchants.Protection");
    private final java.util.Set<String> warned = ConcurrentHashMap.newKeySet();

    /** @param providers the bridges to AND (an immutable copy is taken); empty ⇒ allow-all */
    public ProtectionService(List<ProtectionProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** Whether {@code actor} may have an ability act at {@code where}; allow-all when no providers are wired. */
    public boolean allows(UUID actor, Location where) {
        if (providers.isEmpty() || actor == null || where == null) {
            return true;
        }
        for (ProtectionProvider provider : providers) {
            try {
                if (!provider.allows(actor, where)) {
                    return false; // first deny wins
                }
            } catch (Throwable failed) {
                if (warned.add(provider.name())) {
                    log.log(System.Logger.Level.WARNING,
                            "protection provider '" + provider.name() + "' threw; treating as allow", failed);
                }
            }
        }
        return true;
    }

    /** How many providers are composed — for the boot log. */
    public int providerCount() {
        return providers.size();
    }
}
