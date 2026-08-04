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

    /** One in-flight shot: the turret owner's id (may be {@code null} — no owner, so no IMPACT), and whether
     *  its one IMPACT has already been paid. */
    private record Shot(UUID owner, boolean spent) {
    }

    private static final Set<UUID> TURRETS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Shot> SHOTS = new ConcurrentHashMap<>();

    /** Track a freshly-placed emplacement (its blast never touches terrain, and it is never a shot). */
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
        if (projectile != null) {
            SHOTS.put(projectile, new Shot(owner, false));
        }
    }

    /**
     * Claim {@code projectile}'s single IMPACT — the owner on the first strike, {@code null} on every later
     * one. An explosive shot damages its victim and then its blast damages them again; only the first is the
     * hit the ability paid for.
     */
    public static UUID claimImpact(UUID projectile) {
        if (projectile == null) {
            return null;
        }
        // compute() remaps under the bin lock, so two region threads delivering the same strike cannot both
        // read an unspent row and each pay the payload.
        UUID[] owner = {null};
        SHOTS.computeIfPresent(projectile, (id, shot) -> {
            if (shot.spent()) {
                return shot;
            }
            owner[0] = shot.owner();
            return new Shot(shot.owner(), true);
        });
        return owner[0];
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
