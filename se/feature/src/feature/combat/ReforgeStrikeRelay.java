package feature.combat;

import engine.stores.EngineStores;
import engine.stores.HitTempoStore;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Thread-confined identity relay for the reforge armed-window strikes (ADR-0071), the {@link ParkFlushRelay}
 * shape. The combat dispatch OPTIMISTICALLY folds a Quickening tax / an Unhanding malus / a Supernova discharge
 * into an in-flight hit and marks here which windows it touched; the MONITOR confirm
 * ({@link ReforgeStrikeListener}) COMMITS the consumption (the i-frame write, the window consume, the core
 * spend) iff the event survived — a Dodge/Inversion cancel keeps every window armed and every charge intact,
 * because the optimistic fold contributions died with the cancelled event.
 *
 * <p>The mark is keyed by BOTH the event and the {@link EngineStores} identity (the ADR-0069 parallel-triples
 * lesson): a single event fired on one thread is delivered to EVERY registered dispatch+listener pair, and the
 * live tester stands up many independent stores triples at once, so without the stores key a foreign listener
 * would consume a mark belonging to another aggregate. Event identity keeps a stale mark from an earlier hit
 * harmless.
 */
final class ReforgeStrikeRelay {

    /** Which reforge windows this in-flight hit consumed optimistically, pinned to the event AND the stores. */
    record Pending(EntityDamageByEntityEvent event, EngineStores stores, UUID attacker, UUID victim,
                   boolean tempoHit, HitTempoStore.Window tempoWindow, boolean disarmStrike,
                   boolean batterySpend) {
    }

    /** The neutral accumulator base — a hit that has touched no window yet (never published as a mark). */
    private static final Pending EMPTY = new Pending(null, null, null, null, false, null, false, false);

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private ReforgeStrikeRelay() {
    }

    /** Accumulate a Quickening tempo hit onto {@code base} ({@code null} = the first concern this hit). */
    static Pending tempo(Pending base, HitTempoStore.Window window) {
        Pending b = base == null ? EMPTY : base;
        return new Pending(null, null, null, null, true, window, b.disarmStrike(), b.batterySpend());
    }

    /** Accumulate an Unhanding strike onto {@code base}. */
    static Pending disarm(Pending base) {
        Pending b = base == null ? EMPTY : base;
        return new Pending(null, null, null, null, b.tempoHit(), b.tempoWindow(), true, b.batterySpend());
    }

    /** Accumulate a Supernova discharge onto {@code base}. */
    static Pending battery(Pending base) {
        Pending b = base == null ? EMPTY : base;
        return new Pending(null, null, null, null, b.tempoHit(), b.tempoWindow(), b.disarmStrike(), true);
    }

    /** Publish the accumulated concerns for {@code event}, pinned to its {@code stores} + the two parties. */
    static void mark(EntityDamageByEntityEvent event, EngineStores stores, UUID attacker, UUID victim,
                     Pending pending) {
        PENDING.set(new Pending(event, stores, attacker, victim, pending.tempoHit(), pending.tempoWindow(),
                pending.disarmStrike(), pending.batterySpend()));
    }

    /**
     * Return-and-clear the pending iff it belongs to {@code event} AND {@code stores} (identity on both); else
     * {@code null} — a stale mark, or one another aggregate's dispatch left on this thread, is ignored.
     */
    static Pending consume(EntityDamageByEntityEvent event, EngineStores stores) {
        Pending pending = PENDING.get();
        if (pending != null && pending.event() == event && pending.stores() == stores) {
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
