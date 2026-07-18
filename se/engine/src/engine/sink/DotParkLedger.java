package engine.sink;

import engine.stores.PlayerScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.LivingEntity;

/**
 * The ONE per-boot ledger banking engine DoT damage that would otherwise break a Mental combo (ADR-0069):
 * while Mental holds an active combo on a player, a mid-combo hurt re-arms the vanilla immunity window and
 * starves the combo's shipped-knock feed, so SE's would-be hurt is parked here per (victim, attacker) and
 * released into the combo holder's next hit or the combo-end paced release. Carried on {@link SinkEnv} like
 * the other per-boot ledgers; keyed by victim UUID with per-entry {@code synchronized} mutation so region
 * migration between ticks (a different OS thread each tick, never two at once) still gets a happens-before edge.
 */
public final class DotParkLedger implements PlayerScoped {

    /** Combo-state staleness belt: a start with no end after this is treated as expired (Mental combos end in seconds). */
    public static final long COMBO_TTL_TICKS = 600L;
    /** Key for damage with no (or self) attribution. */
    static final UUID UNATTRIBUTED = new UUID(0L, 0L);

    /**
     * One attacker's banked damage on one victim. {@code attackerHandle} is handed to vanilla as the damage
     * source (never dereferenced here — ComboDotRelease's one liveness read is Regions.read-guarded);
     * {@code firstParkedTick} orders the paced release oldest-first and pins the restore-merge age.
     */
    public record Bucket(UUID attackerId, LivingEntity attackerHandle, double amount, long firstParkedTick) { }

    /** Combo state + banked buckets for one victim; every mutation holds the entry's monitor. */
    private static final class VictimEntry {
        boolean comboPresent;
        long sinceTick;
        final Map<UUID, Bucket> buckets = new HashMap<>();
    }

    private final ConcurrentHashMap<UUID, VictimEntry> victims = new ConcurrentHashMap<>();
    /** Release in-flight guard, cleared by {@link #clear} so the quit sweep / death listener drops it (§8 Folia leak fix). */
    private final Set<UUID> releaseInFlight = ConcurrentHashMap.newKeySet();

    /** Mark a combo active on {@code victim}. Holder identity is unused — parking is per-victim, not per-holder. */
    public void comboStarted(UUID victim, UUID attackerOrNull, long nowTick) {
        VictimEntry entry = victims.computeIfAbsent(victim, k -> new VictimEntry());
        synchronized (entry) {
            entry.comboPresent = true;
            entry.sinceTick = nowTick;
        }
    }

    /** Remove combo state; banked buckets stay for release. */
    public void comboEnded(UUID victim) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.comboPresent = false;
            pruneIfEmpty(victim, entry);
        }
    }

    public boolean comboActive(UUID victim, long nowTick) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return active(entry, nowTick);
        }
    }

    /** Bank one would-be hurt. Returns false (caller applies normally) when no active combo on victim. */
    public boolean tryPark(UUID victim, LivingEntity attackerOrNull, double amount, long nowTick) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (!active(entry, nowTick)) {
                return false;
            }
            UUID key = attackerOrNull == null ? UNATTRIBUTED : attackerOrNull.getUniqueId();
            Bucket existing = entry.buckets.get(key);
            if (existing == null) {
                entry.buckets.put(key, new Bucket(key, attackerOrNull, amount, nowTick));
            } else {
                // Sum the debt, keep the original age, refresh to the newest non-null handle.
                LivingEntity handle = attackerOrNull != null ? attackerOrNull : existing.attackerHandle();
                entry.buckets.put(key, new Bucket(key, handle, existing.amount() + amount, existing.firstParkedTick()));
            }
            return true;
        }
    }

    /** Remove + return the buckets a hit by {@code attacker} may join: bucket[attacker] + bucket[UNATTRIBUTED]. */
    public List<Bucket> drainFor(UUID victim, UUID attacker) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return List.of();
        }
        synchronized (entry) {
            List<Bucket> out = new ArrayList<>(2);
            Bucket own = entry.buckets.remove(attacker);
            if (own != null) {
                out.add(own);
            }
            if (!UNATTRIBUTED.equals(attacker)) {
                Bucket un = entry.buckets.remove(UNATTRIBUTED);
                if (un != null) {
                    out.add(un);
                }
            }
            pruneIfEmpty(victim, entry);
            return out;
        }
    }

    /** Re-insert buckets after a cancelled flush; merges by attackerId, keeps the OLDER firstParkedTick. */
    public void restore(UUID victim, List<Bucket> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        VictimEntry entry = victims.computeIfAbsent(victim, k -> new VictimEntry());
        synchronized (entry) {
            for (Bucket b : buckets) {
                Bucket existing = entry.buckets.get(b.attackerId());
                if (existing == null) {
                    entry.buckets.put(b.attackerId(), b);
                } else {
                    long olderTick = Math.min(existing.firstParkedTick(), b.firstParkedTick());
                    LivingEntity handle = existing.attackerHandle() != null ? existing.attackerHandle() : b.attackerHandle();
                    entry.buckets.put(b.attackerId(),
                            new Bucket(b.attackerId(), handle, existing.amount() + b.amount(), olderTick));
                }
            }
        }
    }

    /**
     * Oldest bucket, or null while a combo is active on the victim (an attributed release hurt is a melee to
     * Mental — a third-party knock would END the combo, ComboTracker switched-attacker — so NOTHING is eligible
     * mid-combo; buckets wait, delayed not lost) or when empty. Re-checks eligibility per call, so a combo
     * re-forming mid-release automatically re-parks everything.
     */
    public Bucket drainOldestEligible(UUID victim, long nowTick) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (active(entry, nowTick)) {
                return null;
            }
            Bucket oldest = null;
            for (Bucket b : entry.buckets.values()) {
                if (oldest == null || b.firstParkedTick() < oldest.firstParkedTick()) {
                    oldest = b;
                }
            }
            if (oldest != null) {
                entry.buckets.remove(oldest.attackerId());
                pruneIfEmpty(victim, entry);
            }
            return oldest;
        }
    }

    public boolean hasParked(UUID victim) {
        VictimEntry entry = victims.get(victim);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return !entry.buckets.isEmpty();
        }
    }

    /** Release in-flight guard, CAS semantics: true = caller owns the (single) release loop for this victim. */
    public boolean beginRelease(UUID victim) {
        return releaseInFlight.add(victim);
    }

    /** Idempotent release-guard drop (loop stop). */
    public void endRelease(UUID victim) {
        releaseInFlight.remove(victim);
    }

    /** PlayerScoped quit sweep + death clear: combo state AND buckets AND the release in-flight flag. */
    @Override
    public void clear(UUID player) {
        victims.remove(player);
        releaseInFlight.remove(player);
    }

    private static boolean active(VictimEntry entry, long nowTick) {
        return entry.comboPresent && nowTick - entry.sinceTick <= COMBO_TTL_TICKS;
    }

    /** Value-equality remove (never drop a replacement entry) once a victim has neither combo state nor debt. */
    private void pruneIfEmpty(UUID victim, VictimEntry entry) {
        if (!entry.comboPresent && entry.buckets.isEmpty()) {
            victims.remove(victim, entry);
        }
    }
}
