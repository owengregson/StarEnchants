package engine.stores;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The {@code EngineStores} member: per-player {@link WhyRing}s plus the live snapshot generation stamped into
 * every record (ADR-0045), so a cross-reload row renders as pre-reload instead of resolving against the wrong
 * tables. A ring is allocated lazily on a player's first attempt and freed on quit ({@link #clear}).
 */
public final class WhyStore implements PlayerScoped, WhyRecorder {

    // Shared constant so the miss path captures nothing and allocates only the ring itself (once per session).
    private static final Function<UUID, WhyRing> NEW_RING = k -> new WhyRing();

    private final ConcurrentHashMap<UUID, WhyRing> rings = new ConcurrentHashMap<>();
    private volatile int generation;

    /** Bind the live snapshot generation; the composition root calls this at boot and on every reload publish. */
    public void generation(int generation) {
        this.generation = generation;
    }

    @Override
    public void record(UUID actor, long nowTicks, int triggerId, int defId, int verdict, int pA, int pB) {
        // get-then-computeIfAbsent: the JDK-8 CHM computeIfAbsent can bin-lock on a present key (legacy jar).
        WhyRing ring = rings.get(actor);
        if (ring == null) {
            ring = rings.computeIfAbsent(actor, NEW_RING);
        }
        ring.record(nowTicks, WhyRing.packMeta(verdict, triggerId, generation), defId, pA, pB);
    }

    /** The player's attempts newest-first (a copied snapshot), or {@link List#of()} if none recorded. */
    public List<WhyRing.Attempt> attempts(UUID player) {
        WhyRing ring = rings.get(player);
        return ring == null ? List.of() : ring.copy();
    }

    @Override
    public void clear(UUID player) {
        rings.remove(player);
    }

    public void clearAll() {
        rings.clear();
    }
}
