package feature.combat;

import engine.sink.DotParkLedger;
import engine.sink.EngineDamage;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * The combo-end / post-combo paced release of parked DoT damage (ADR-0069): one banked bucket per hurt, each
 * applied only when the victim's vanilla hit-window is clear, oldest debt first, attributed to the bucket's
 * original attacker. Runs on the victim's owning thread; releases NOTHING while a combo is active (the ledger
 * returns null then), so a combo re-forming mid-release automatically re-parks everything. The
 * single-release-per-victim guard lives in the ledger — not here — so the quit sweep / death clear can drop
 * it: on Folia a retired entity task never runs its body again, so no cleanup owned by this class would run.
 */
public final class ComboDotRelease {

    /** Honest-effort bound on waiting for a clear window — 4× the vanilla half-window, so a foreign re-arm can't stall forever. */
    private static final int WINDOW_WAIT_CAP_TICKS = 40;
    /** Absolute loop lifetime; on expiry drain all remaining eligible buckets back-to-back and stop. */
    private static final int LOOP_MAX_TICKS = 200;

    private final DotParkLedger ledger;
    private final LongSupplier nowTicks;

    public ComboDotRelease(DotParkLedger ledger, LongSupplier nowTicks) {
        this.ledger = ledger;
        this.nowTicks = nowTicks;
    }

    /** Arm the paced release for {@code victim}; a no-op while a release is already in flight for them. */
    public void begin(Player victim) {
        UUID uuid = victim.getUniqueId();
        if (!ledger.beginRelease(uuid)) {
            return;
        }
        // Per-loop mutable state as one-element captures: a lambda capture must be effectively final, and
        // instance fields would be shared across concurrent victims (the FREEZE-chain shape, DispatchSinkBase).
        TaskHandle[] handle = new TaskHandle[1];
        int[] windowWait = {0};
        int[] lifetime = {0};
        handle[0] = Scheduling.repeatingEntity(victim, 1L, 1L, () -> {
            // Paper self-heal: the Bukkit timer is not entity-tied, so a quit/dead victim reaches this branch;
            // on Folia the task is already retired and the quit sweep's clear did the cleanup.
            if (!victim.isValid() || victim.isDead()) {
                ledger.clear(uuid);
                stop(handle, uuid);
                return;
            }
            ++lifetime[0];
            boolean windowClear = victim.getNoDamageTicks() <= victim.getMaximumNoDamageTicks() / 2;
            if (!windowClear && ++windowWait[0] <= WINDOW_WAIT_CAP_TICKS && lifetime[0] <= LOOP_MAX_TICKS) {
                return;
            }
            windowWait[0] = 0;
            DotParkLedger.Bucket bucket = ledger.drainOldestEligible(uuid, nowTicks.getAsLong());
            if (bucket == null) {
                // Empty OR a combo re-formed — either way stop; parked buckets wait for the next trigger.
                stop(handle, uuid);
                return;
            }
            apply(victim, bucket);
            if (lifetime[0] > LOOP_MAX_TICKS) {
                // Loop lifetime spent: drain all remaining eligible debt back-to-back — bounded execution beats a
                // perfect window; buckets a re-formed combo has made ineligible stay parked for the next trigger.
                for (DotParkLedger.Bucket rest; (rest = ledger.drainOldestEligible(uuid, nowTicks.getAsLong())) != null; ) {
                    apply(victim, rest);
                }
                stop(handle, uuid);
            }
        });
    }

    /** Apply one bucket, attributed to its attacker where that handle is live — the ONE Regions-guarded foreign read. */
    private static void apply(Player victim, DotParkLedger.Bucket bucket) {
        LivingEntity attacker = bucket.attackerHandle();
        boolean attackerLive = attacker != null
                && Regions.read("combo-dot-release attacker", attacker::isValid, Boolean.FALSE);
        EngineDamage.hurt(victim, bucket.amount(), attackerLive ? attacker : null);
    }

    /** Cancel the loop and drop the ledger's in-flight flag (idempotent — clear may already have dropped it). */
    private void stop(TaskHandle[] handle, UUID uuid) {
        if (handle[0] != null) {
            handle[0].cancel();
        }
        ledger.endRelease(uuid);
    }
}
