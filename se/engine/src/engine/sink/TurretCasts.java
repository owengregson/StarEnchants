package engine.sink;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient registry of every live {@code TURRET_RING} emplacement and every shot one has fired, so the
 * feature layer can (a) keep both out of the terrain — a turret's own blast and its projectile's are cancelled,
 * the gap contract's "never grief blocks" — and (b) fire the turret owner's {@code IMPACT} abilities on
 * whatever a shot strikes. The {@link FallingBlockCasts} shape: era-agnostic (no entity PDC, which 1.8 lacks;
 * no plugin-bound metadata) and cleared on disable.
 *
 * <p>A shot's IMPACT is claimed ONCE ({@link #claimImpact}) but its row survives the claim, because an
 * explosive projectile damages, then explodes: the blast must still be recognised as ours after the strike has
 * been paid. The row is dropped at the blast, or at the shot's TTL when it never hits anything.
 */
public final class TurretCasts {

    private TurretCasts() {
    }

    /** One in-flight shot: the turret owner's id (may be {@code null} — no owner, so no IMPACT), the group that
     *  armed the ring, and whether its one IMPACT has already been paid. */
    private record Shot(UUID owner, int sourceGroup, boolean spent, int gen) {
    }

    /** A claimed strike: who pays the payload, and which authored {@code group:} it is scoped to (ADR-0074). */
    public record Impact(UUID owner, int sourceGroup) {
    }

    private static final Set<UUID> TURRETS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Shot> SHOTS = new ConcurrentHashMap<>();

    /**
     * Track a freshly-placed emplacement (its blast never touches terrain, and it is never a shot).
     *
     * <p>No group is stored here. A turret outlives the activation that placed it, but its volley chain already
     * carries the owner and the profile through its own re-arming closure, so the IMPACT scoping group rides
     * there too — captured once, correct for every shot, and never a registry read that could fail OPEN.
     */
    public static void bindTurret(UUID turret) {
        if (turret != null) {
            TURRETS.add(turret);
        }
    }

    /** Forget an emplacement — called BEFORE the body is removed, the summon-path teardown order. */
    public static void forgetTurret(UUID turret) {
        TURRETS.remove(turret);
    }

    /** Track a shot a turret just launched; {@code owner} is who pays its IMPACT ({@code null} = nobody). */
    public static void bindShot(UUID projectile, UUID owner) {
        bindShot(projectile, owner, -1, CastGeneration.current());
    }

    /** {@link #bindShot(UUID, UUID)} carrying its turret's group all the way to the strike. */
    public static void bindShot(UUID projectile, UUID owner, int sourceGroup, int gen) {
        if (projectile != null) {
            // R-QC58: the ARM generation, captured by the volley chain — not the live one. A turret that
            // outlives a reload keeps firing, and its shots must stop claiming a group id that moved.
            SHOTS.put(projectile, new Shot(owner, sourceGroup, false, gen));
        }
    }

    /**
     * Claim {@code projectile}'s single IMPACT — the owner + scoping group on the first strike, {@code null} on
     * every later one. An explosive shot damages its victim and then its blast damages them again; only the
     * first is the hit the ability paid for.
     */
    public static Impact claimImpact(UUID projectile) {
        if (projectile == null) {
            return null;
        }
        // compute() remaps under the bin lock, so two region threads delivering the same strike cannot both
        // read an unspent row and each pay the payload.
        Impact[] claimed = {null};
        SHOTS.computeIfPresent(projectile, (id, shot) -> {
            if (shot.spent()) {
                return shot;
            }
            // R-QC58: a shot armed under an older snapshot is SPENT without paying — dropped, never unscoped,
            // since an unscoped strike fires the owner's whole IMPACT roster instead of the ring's own payload.
            claimed[0] = shot.owner() == null || CastGeneration.stale(shot.gen())
                    ? null : new Impact(shot.owner(), shot.sourceGroup());
            return new Shot(shot.owner(), shot.sourceGroup(), true, shot.gen());
        });
        return claimed[0];
    }

    /** Forget a shot (its blast landed, or its TTL elapsed without a hit). */
    public static void forgetShot(UUID projectile) {
        SHOTS.remove(projectile);
    }

    /** Whether {@code entity} is an emplacement or one of its shots — the pair whose blasts never break blocks. */
    public static boolean neverGriefs(UUID entity) {
        return entity != null && (TURRETS.contains(entity) || SHOTS.containsKey(entity));
    }

    /** Drop all tracking (call on disable). */
    public static void clearAll() {
        TURRETS.clear();
        SHOTS.clear();
    }
}
