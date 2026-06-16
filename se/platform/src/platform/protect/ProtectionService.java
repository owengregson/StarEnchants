package platform.protect;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The composed protection gate the engine consults (docs/architecture.md §2, §3.3 gate 2). ANDs every
 * registered {@link ProtectionProvider}: an action is allowed iff <em>all</em> providers allow it
 * (deny is authoritative). With no providers it allows everything — a server with no land plugin has
 * nothing to protect. A provider that throws is treated as "allow" and logged once, so a buggy bridge
 * degrades to permissive rather than blocking all enchant activity.
 *
 * <p>Results are cached per player for the current tick (the gate-2 perf requirement): one combat hit
 * walks many abilities through gate 2 at the same location, so the providers are queried once per
 * (player, block) per tick, not once per ability. A cached decision is valid only for the exact tick
 * it was taken on, so it self-invalidates the moment the tick advances (or the queried block changes);
 * the map is concurrent so distinct players' region threads never contend, and it self-prunes dead
 * (stale-tick) entries when it grows past a soft cap, so it stays bounded without any quit hook.
 */
public final class ProtectionService {

    /** Above this many cached players, prune entries from earlier ticks (they can never be read again). */
    private static final int PRUNE_THRESHOLD = 256;

    private final List<ProtectionProvider> providers;
    private final LongSupplier nowTicks;
    private final System.Logger log = System.getLogger("StarEnchants.Protection");
    private final ConcurrentHashMap<java.util.UUID, Decision> cache = new ConcurrentHashMap<>();
    private final java.util.Set<String> warned = ConcurrentHashMap.newKeySet();

    /**
     * @param providers the bridges to AND (an immutable copy is taken); empty ⇒ allow-all
     * @param nowTicks  the monotonic game-tick source used to scope the per-tick cache
     */
    public ProtectionService(List<ProtectionProvider> providers, LongSupplier nowTicks) {
        this.providers = List.copyOf(providers);
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /** Whether {@code actor} may have an ability act at {@code where}; allow-all when no providers are wired. */
    public boolean allows(Player actor, Location where) {
        if (providers.isEmpty() || actor == null || where == null) {
            return true;
        }
        long tick = nowTicks.getAsLong();
        Decision cached = cache.get(actor.getUniqueId());
        if (cached != null && cached.matches(tick, where)) {
            return cached.allowed;
        }
        boolean allowed = computeAllows(actor, where);
        cache.put(actor.getUniqueId(), Decision.of(tick, where, allowed));
        if (cache.size() > PRUNE_THRESHOLD) {
            cache.values().removeIf(d -> d.tick != tick); // earlier-tick entries can never match again
        }
        return allowed;
    }

    /** How many providers are composed — for the boot log. */
    public int providerCount() {
        return providers.size();
    }

    private boolean computeAllows(Player actor, Location where) {
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

    /** A cached allow/deny scoped to one tick and one block (block granularity is enough for region checks). */
    private record Decision(long tick, java.util.UUID world, int x, int y, int z, boolean allowed) {

        static Decision of(long tick, Location at, boolean allowed) {
            java.util.UUID worldId = at.getWorld() == null ? null : at.getWorld().getUID();
            return new Decision(tick, worldId, at.getBlockX(), at.getBlockY(), at.getBlockZ(), allowed);
        }

        boolean matches(long now, Location at) {
            return tick == now
                    && at.getBlockX() == x && at.getBlockY() == y && at.getBlockZ() == z
                    && Objects.equals(world, at.getWorld() == null ? null : at.getWorld().getUID());
        }
    }
}
