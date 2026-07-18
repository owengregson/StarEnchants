package engine.sink;

import engine.stores.PlayerScoped;
import engine.stores.RecentAttackersStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import platform.caps.Regions;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * Attacker-cloud targeting for SPAWN_SWARM summons (ADR-0068): per swarm owner, ONE publisher task on
 * the owner's entity scheduler holds the most-recent attacker (entity ref stamped at-hit by
 * CombatDispatch — the ADR-0043 capture-then-guarded-read shape) and publishes an immutable
 * facing-pillar snapshot the bats' steer tasks consume. A bat task never touches the owner or
 * attacker entity. Static + era-agnostic (the {@link SwarmSpawns} pattern); an entry dies with its
 * swarm, its owner's quit ({@link #scope}), its TTL deadline, or disable ({@link #clearAll}).
 */
public final class SwarmClouds {

    /** The published pillar-base snapshot: the attacker's feet + 1 block along their facing, tick-stamped. */
    public record CloudTarget(double x, double y, double z, long stampTick) {
    }

    /** How long a recorded hit keeps its attacker cloud-eligible — the gank window's 10 s, restated. */
    public static final int WINDOW_TICKS = 200;
    /** Consumers ignore a target older than this — the silently-retired-publisher degrade horizon. */
    public static final int STALE_TICKS = 40;
    /** Publisher hard deadline slack past the swarm TTL — the backstop when teardown signals are lost. */
    static final int DEADLINE_SLACK_TICKS = 100;
    /** Engage at dist ≤ cloud-range; release only past cloud-range + this — no boundary flicker. */
    static final double RANGE_HYSTERESIS = 1.0;

    private static final class OwnerCloud {
        volatile CloudTarget target;
        volatile Entity attacker;
        volatile long attackerTick;
        volatile long deadlineTick;
        volatile double rangeSq;
        volatile double releaseRangeSq;
        volatile boolean seeded;
        volatile boolean turned; // ADR-0071 CONVERT_SUMMON: permanently target the former owner themself
        volatile TaskHandle publisher;
        final Set<Entity> bats = ConcurrentHashMap.newKeySet();
    }

    private static final Map<UUID, OwnerCloud> LIVE = new ConcurrentHashMap<>();

    private SwarmClouds() {
    }

    /** CombatDispatch producer: stamp {@code attacker} as {@code owner}'s most recent (overwrite = recency). No-op unless a cloud is armed. */
    public static void noteHit(UUID owner, Entity attacker, long nowTicks) {
        OwnerCloud cloud = LIVE.get(owner);
        if (cloud == null || attacker == null || cloud.turned) {
            return; // a turned cloud (Bell-converted) permanently targets its former owner — ignore new hits
        }
        cloud.attacker = attacker;
        cloud.attackerTick = nowTicks;
    }

    /**
     * Bell conversion (ADR-0071 CONVERT_SUMMON): TURN the cloud containing {@code batId}, unless it
     * belongs to {@code ringer} or is already turned. A turned cloud permanently publishes its own owner
     * as the pillar target (the swarm orbits/blinds its former owner), ignores further {@link #noteHit}
     * stamps and the recency window, and dies exactly as before (TTL deadline, owner quit, disable).
     * Returns the former owner's UUID iff THIS call flipped the cloud (null: the ringer's own cloud,
     * already turned, or an untracked bat) — the caller counts each cloud once however many of its bats
     * the enumeration yields. Pure map/set reads plus one volatile flag write: membership is matched by
     * {@code getUniqueId()} over each cloud's bats set (UUIDs are immutable — region-free on Folia), so
     * this is exactly as region-safe as the caller's own {@code getNearbyEntities} enumeration; no bat
     * position is ever read.
     */
    public static UUID turnByBat(UUID ringer, UUID batId) {
        if (batId == null) {
            return null;
        }
        for (Map.Entry<UUID, OwnerCloud> entry : LIVE.entrySet()) {
            UUID ownerId = entry.getKey();
            if (ownerId.equals(ringer)) {
                continue; // the ringer's own cloud never turns on them
            }
            OwnerCloud cloud = entry.getValue();
            boolean member = false;
            for (Entity bat : cloud.bats) {
                if (batId.equals(bat.getUniqueId())) { // UUID read only — immutable, region-free
                    member = true;
                    break;
                }
            }
            if (!member) {
                continue;
            }
            if (cloud.turned) {
                return null; // already turned — the caller counts each cloud once
            }
            cloud.turned = true;
            return ownerId; // the former owner, now the swarm's permanent target
        }
        return null; // untracked bat
    }

    /** Arm (or refresh) {@code owner}'s cloud: the entry plus ONE publisher task on the owner's scheduler. */
    public static void arm(Player owner, double range, int ttlTicks, LongSupplier nowTicks,
                           Supplier<RecentAttackersStore.LastAttacker> seed) {
        long now = nowTicks.getAsLong();
        // Reap silently-orphaned entries (cold path) VIA drop(): cancelling the handle here matters —
        // an orphan publisher's next-tick self-check is defeated if its owner re-arms in the same tick
        // (the fresh entry makes LIVE.get non-null and the orphan would live on beside the new task).
        for (UUID orphan : LIVE.keySet()) {
            OwnerCloud stale = LIVE.get(orphan);
            if (stale != null && now > stale.deadlineTick) {
                drop(orphan);
            }
        }
        UUID ownerId = owner.getUniqueId();
        OwnerCloud cloud = LIVE.computeIfAbsent(ownerId, k -> new OwnerCloud());
        cloud.rangeSq = range * range;
        cloud.releaseRangeSq = (range + RANGE_HYSTERESIS) * (range + RANGE_HYSTERESIS);
        cloud.deadlineTick = now + Math.max(1, ttlTicks) + DEADLINE_SLACK_TICKS;
        if (cloud.publisher != null) {
            return; // re-summon while live: the one publisher carries on with the refreshed range/deadline
        }
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = Scheduling.repeatingEntity(owner, 1L, 1L,
                () -> publish(ownerId, owner, nowTicks, seed, handle));
        cloud.publisher = handle[0];
    }

    /** Register a live swarm summon under {@code owner} — the publisher's liveness census. */
    public static void track(UUID owner, Entity bat) {
        OwnerCloud cloud = LIVE.get(owner);
        if (cloud != null) {
            cloud.bats.add(bat);
        }
    }

    /** The owner's current pillar target, or {@code null} when idle/stale — bats then fall back to the damp. */
    public static CloudTarget target(UUID owner, long nowTicks) {
        OwnerCloud cloud = LIVE.get(owner);
        if (cloud == null) {
            return null;
        }
        CloudTarget target = cloud.target;
        return target == null || nowTicks - target.stampTick() > STALE_TICKS ? null : target;
    }

    private static void publish(UUID ownerId, Player owner, LongSupplier nowTicks,
                                Supplier<RecentAttackersStore.LastAttacker> seed, TaskHandle[] handle) {
        OwnerCloud cloud = LIVE.get(ownerId);
        long now = nowTicks.getAsLong();
        if (cloud == null || !owner.isValid() || now > cloud.deadlineTick || pruneToEmpty(cloud)) {
            drop(ownerId);
            if (handle[0] != null) {
                handle[0].cancel();
            }
            return;
        }
        if (cloud.turned) {
            // Bell-converted: permanently target the FORMER owner themself (this task runs on the owner's
            // thread, so reading their own location is region-local). The bats' steer tasks keep their
            // original owner-UUID key, so the whole swarm converges on the former owner with no re-arm.
            Location opos = owner.getLocation();
            float oyaw = opos.getYaw();
            cloud.target = new CloudTarget(
                    opos.getX() + SwarmRing.offsetX(oyaw, SwarmRing.CLOUD_PILLAR_FORWARD),
                    opos.getY(),
                    opos.getZ() + SwarmRing.offsetZ(oyaw, SwarmRing.CLOUD_PILLAR_FORWARD), now);
            return;
        }
        if (!cloud.seeded) {
            cloud.seeded = true;
            seedFromRecentAttackers(cloud, owner, seed);
        }
        Entity attacker = cloud.attacker;
        if (attacker == null || now - cloud.attackerTick >= WINDOW_TICKS) {
            cloud.target = null; // window lapsed (or nothing yet): vanilla AI
            return;
        }
        try {
            if (!attacker.isValid() || (attacker instanceof Player p && !p.isOnline())) {
                cloud.attacker = null;
                cloud.target = null;
                return;
            }
            Location apos = attacker.getLocation(); // possibly cross-region: guarded, fail-closed (ADR-0043 residual)
            Location opos = owner.getLocation();    // region-local: this task runs on the owner's thread
            double dx = apos.getX() - opos.getX();
            double dy = apos.getY() - opos.getY();
            double dz = apos.getZ() - opos.getZ();
            // Hysteresis: engage inside cloud-range; once engaged, release only past the +1-block band —
            // a boundary-straddling attacker must not flip the swarm orbit↔damp on successive ticks.
            if (dx * dx + dy * dy + dz * dz > (cloud.target != null ? cloud.releaseRangeSq : cloud.rangeSq)) {
                cloud.target = null; // out of "nearby" — vanilla AI until they re-enter within the window
                return;
            }
            float yaw = apos.getYaw();
            cloud.target = new CloudTarget(
                    apos.getX() + SwarmRing.offsetX(yaw, SwarmRing.CLOUD_PILLAR_FORWARD),
                    apos.getY(),
                    apos.getZ() + SwarmRing.offsetZ(yaw, SwarmRing.CLOUD_PILLAR_FORWARD), now);
        } catch (RuntimeException unreadable) {
            Regions.swallowed("SwarmClouds.publish", unreadable); // keep the last target; STALE_TICKS bounds the lie
        }
    }

    /** Prune dead summons (guarded flag reads — a faulting read keeps the bat; the deadline bounds it). */
    private static boolean pruneToEmpty(OwnerCloud cloud) {
        cloud.bats.removeIf(bat -> {
            try {
                return !bat.isValid();
            } catch (RuntimeException unreadable) {
                Regions.swallowed("SwarmClouds.prune", unreadable);
                return false;
            }
        });
        return cloud.bats.isEmpty();
    }

    /** First-tick seed: the pre-summon attacker (the gank store's latest UUID) resolved region-locally. */
    private static void seedFromRecentAttackers(OwnerCloud cloud, Player owner,
                                                Supplier<RecentAttackersStore.LastAttacker> seed) {
        RecentAttackersStore.LastAttacker last = seed.get();
        if (last == null || cloud.attacker != null) {
            return; // nothing recorded, or a live hit already beat the seed
        }
        double range = Math.sqrt(cloud.rangeSq);
        for (Entity near : owner.getNearbyEntities(range, range, range)) {
            if (near.getUniqueId().equals(last.attacker())) {
                cloud.attacker = near;
                cloud.attackerTick = last.tick(); // the ORIGINAL hit tick — seeding never extends the window
                return;
            }
        }
    }

    /** Drop one owner's cloud, cancelling its publisher (quit sweep / publisher teardown). */
    static void drop(UUID owner) {
        OwnerCloud cloud = LIVE.remove(owner);
        if (cloud != null && cloud.publisher != null) {
            cloud.publisher.cancel();
        }
    }

    /** The quit-sweep adapter: a quitting owner's cloud dies with them (the PetsModule .store row). */
    public static PlayerScoped scope() {
        return SwarmClouds::drop;
    }

    /** Disable teardown: cancel every publisher and clear the registry. */
    public static void clearAll() {
        for (UUID owner : LIVE.keySet()) {
            drop(owner);
        }
    }
}
