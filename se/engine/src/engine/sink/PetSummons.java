package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of live flagged summons (ADR-0052): {@code spawned entity UUID → its} {@link SummonFlags}. The
 * {@code GuardianCasts} pattern — a static concurrent map keyed by entity UUID, deliberately era-agnostic
 * (1.8 has no entity PDC) and deliberately separate from {@code GuardianCasts} (owner-tagging serves
 * GUARDIAN_HURT; double-purposing it would fire owner abilities on every summon hit). Read by the
 * summon-guard listener on the entity's own region thread; entries drop with the TTL removal, on
 * detonation, and wholesale on disable.
 *
 * <p>Each row carries the snapshot generation it was bound under (R-QC58): {@link SummonFlags#sourceGroup()}
 * is an INTERNED id, so a summon that outlives a reload names a group that no longer means what it did.
 * A stale row reads as untracked — dropped, never unscoped.
 */
public final class PetSummons {

    /** The flags a summon was spawned with, plus the snapshot generation that interned their group id. */
    private record Tracked(SummonFlags flags, int gen) {
    }

    private static final Map<UUID, Tracked> FLAGS = new ConcurrentHashMap<>();

    private PetSummons() {
    }

    public static void bind(UUID entity, SummonFlags flags) {
        FLAGS.put(entity, new Tracked(flags, CastGeneration.current()));
    }

    /**
     * The flags a tracked summon was spawned with, or {@code null} for an untracked entity — and for one
     * bound under an older snapshot, whose interned group id no longer names the group it was armed with.
     */
    public static SummonFlags flags(UUID entity) {
        Tracked tracked = FLAGS.get(entity);
        return tracked == null || CastGeneration.stale(tracked.gen()) ? null : tracked.flags();
    }

    public static void forget(UUID entity) {
        FLAGS.remove(entity);
    }

    /** onDisable teardown — the module's Stop, which only {@code ModuleFold#stop} runs. A RELOAD does not
     *  clear this: live summons survive it, and the generation stamp is what keeps their scoping honest. */
    public static void clearAll() {
        FLAGS.clear();
    }
}
