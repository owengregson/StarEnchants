package engine.sink;

import java.util.UUID;
import org.bukkit.Location;

/**
 * The sink's view of the composed protection gate (docs/architecture.md §3.3 gate 2) — a seam because the
 * providers are discovered from the running server by {@code platform.protect}, which no engine class may
 * reach. Wired at the composition root from the ONE {@code ProtectionService}, so a per-site query and the
 * pipeline's gate-2 query answer from the same provider list.
 *
 * <p>Asked once PER SITE, not once per activation: an ability that places bodies on several spots has to ask
 * about each spot. The service is deliberately uncached, so N sites cost N provider walks — the sanctioned
 * price of a correct answer at a boundary a cache would freeze stale.
 */
@FunctionalInterface
public interface SiteGate {

    /** The inert default for non-root construction sites (tests, tester suites): every site is placeable. */
    SiteGate ALLOW_ALL = (actor, where) -> true;

    /** Whether {@code actor} may have an ability act at {@code where}. */
    boolean allows(UUID actor, Location where);
}
