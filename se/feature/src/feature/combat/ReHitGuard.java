package feature.combat;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Hit identity for the §3.7 once-per-hit contract. Vanilla re-fires an {@link EntityDamageByEntityEvent} for
 * the SAME swing inside the victim's i-frame window ("damage the difference"), but that window is SHARED —
 * fire/poison ticks, engine DoT (ADR-0054) and other attackers arm it too — so a duplicate is only "the same
 * resolved attacker whose landed hit opened (or last continued) the window". The dispatch stamps each landed
 * hit and consults {@link #sameHit}; the per-event skip decision is relayed to MONITOR consumers (rage)
 * through a thread-confined identity mark — exact on Paper and Folia because damage events fire synchronously
 * on the victim-owning thread (the {@code EngineDamage} argument, ADR-0054).
 */
final class ReHitGuard {

    /** Stamps are dead ~half a window (~10 ticks) after writing; anything older only wastes memory. */
    private static final long SWEEP_AGE_TICKS = 100L;
    /** Sweep trigger: bounds the map to victims hit since the last sweep, without a timer. */
    private static final int SWEEP_SIZE = 256;

    private record Stamp(UUID attacker, long tick) {
    }

    private final Map<UUID, Stamp> lastProcessed = new ConcurrentHashMap<>();

    // The event skipped as a duplicate, relayed by IDENTITY: MONITOR runs synchronously after HIGH on the
    // same region thread, and identity-compare makes a stale mark from an earlier event harmless.
    private static final ThreadLocal<EntityDamageByEntityEvent> SKIPPED = new ThreadLocal<>();

    /** True iff an in-window hit continues the stamped hit: same attacker within {@code horizonTicks}. */
    boolean sameHit(UUID victim, UUID attacker, long nowTicks, int horizonTicks) {
        Stamp stamp = lastProcessed.get(victim);
        return stamp != null && stamp.attacker().equals(attacker) && nowTicks - stamp.tick() <= horizonTicks;
    }

    /** Record a landed (non-cancelled) hit as the victim's window opener/continuer. */
    void stamp(UUID victim, UUID attacker, long nowTicks) {
        lastProcessed.put(victim, new Stamp(attacker, nowTicks));
        if (lastProcessed.size() > SWEEP_SIZE) {
            sweep(nowTicks);
        }
    }

    /** Drop dead stamps — mob victims despawn silently, so without this the map grows per victim ever hit. */
    private void sweep(long nowTicks) {
        Iterator<Stamp> it = lastProcessed.values().iterator();
        while (it.hasNext()) {
            if (nowTicks - it.next().tick() > SWEEP_AGE_TICKS) {
                it.remove();
            }
        }
    }

    /** Relay: the dispatch decided {@code event} is a same-swing duplicate. */
    static void markSkipped(EntityDamageByEntityEvent event) {
        SKIPPED.set(event);
    }

    /** Relay: the dispatch processed the current event — clear any stale mark on this thread. */
    static void clearSkipped() {
        SKIPPED.remove();
    }

    /** Whether the dispatch skipped {@code event} as a same-swing duplicate (identity compare). */
    static boolean skipped(EntityDamageByEntityEvent event) {
        return SKIPPED.get() == event;
    }
}
