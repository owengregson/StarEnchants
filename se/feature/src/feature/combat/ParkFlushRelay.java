package feature.combat;

import engine.sink.DotParkLedger;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Thread-confined identity relay for a park FLUSH (ADR-0069), the {@link ReHitGuard} SKIPPED-mark shape: the
 * dispatch drains a victim's parked DoT buckets, folds them into a hit, and marks the event here; the MONITOR
 * confirm ({@link ComboDotSyncListener}) re-parks them iff the event ended cancelled. Exact on Paper and Folia
 * because damage events dispatch synchronously on the victim-owning thread.
 *
 * <p>The mark is keyed by BOTH the event and the source {@link DotParkLedger} (identity). Production runs
 * exactly one dispatch + one ledger, so ledger-scoping is transparent there; but a single event fired on one
 * thread is delivered to EVERY registered {@code CombatDispatch}/{@code ComboDotSyncListener} — the live
 * tester harness stands up many independent dispatch+ledger triples at once — so without the ledger key a
 * foreign listener would consume a mark belonging to another ledger and restore into the wrong one. Ledger
 * identity routes each mark to exactly the listener whose ledger drained it; event identity keeps a stale mark
 * from an earlier event harmless.
 */
final class ParkFlushRelay {

    /** The buckets drained for one in-flight hit, pinned to the event AND the ledger that drained them. */
    record Pending(EntityDamageByEntityEvent event, DotParkLedger ledger, UUID victim,
                   List<DotParkLedger.Bucket> drained) {
    }

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private ParkFlushRelay() {
    }

    static void mark(EntityDamageByEntityEvent event, DotParkLedger ledger, UUID victim,
                     List<DotParkLedger.Bucket> drained) {
        PENDING.set(new Pending(event, ledger, victim, drained));
    }

    /**
     * Return-and-clear the pending iff it belongs to {@code event} AND {@code ledger} (identity on both); else
     * null — a stale mark, or one another ledger's dispatch left on this thread, is ignored.
     */
    static Pending consume(EntityDamageByEntityEvent event, DotParkLedger ledger) {
        Pending pending = PENDING.get();
        if (pending != null && pending.event() == event && pending.ledger() == ledger) {
            PENDING.remove();
            return pending;
        }
        return null;
    }

    /** Unconditional reset of this thread's mark — a test seam ({@code @AfterEach}); production routes by identity. */
    static void clear() {
        PENDING.remove();
    }
}
