package feature.combat;

import engine.sink.DotParkLedger;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Thread-confined identity relay for a park FLUSH (ADR-0069), the {@link ReHitGuard} SKIPPED-mark shape: the
 * dispatch drains a victim's parked DoT buckets, folds them into a hit, and marks the event here; the MONITOR
 * confirm ({@link ComboDotSyncListener}) re-parks them iff the event ended cancelled. Exact on Paper and Folia
 * because damage events dispatch synchronously on the victim-owning thread; {@link #consume} identity-compares
 * so a stale mark from an earlier event on this thread is ignored.
 */
final class ParkFlushRelay {

    /** The buckets drained for one in-flight hit, pinned to the event that drained them. */
    record Pending(EntityDamageByEntityEvent event, UUID victim, List<DotParkLedger.Bucket> drained) {
    }

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private ParkFlushRelay() {
    }

    static void mark(EntityDamageByEntityEvent event, UUID victim, List<DotParkLedger.Bucket> drained) {
        PENDING.set(new Pending(event, victim, drained));
    }

    /** Return-and-clear the pending iff it belongs to {@code event} (identity); else null (stale marks ignored). */
    static Pending consume(EntityDamageByEntityEvent event) {
        Pending pending = PENDING.get();
        if (pending != null && pending.event() == event) {
            PENDING.remove();
            return pending;
        }
        return null;
    }

    /** Drop any stale mark on this thread — the dispatch calls this per hit (ReHitGuard.clearSkipped precedent). */
    static void clear() {
        PENDING.remove();
    }
}
