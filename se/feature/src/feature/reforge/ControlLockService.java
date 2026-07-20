package feature.reforge;

import compile.resolve.PlatformResolvers;
import feature.trigger.TriggerDispatch;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * A reusable combat CONTROL-LOCK (ADR-0071): for a bounded window a victim loses control WITHOUT being frozen in
 * place. Extracted from the Javelin so any future stun arms the same behaviour and adds only its own on-impact
 * effects — the Javelin just calls {@link #arm}. Per victim, for {@code lock} ticks on the victim's own
 * scheduler:
 * <ul>
 *   <li>they are driven down a TRACKED ballistic arc — a velocity seeded from the live knock and re-integrated
 *       each tick ({@link #advanceArc}), then re-asserted EVERY tick, so a camera-snap teleport (which resets a
 *       player's motion) can never stall the fall however hard they spam the view;</li>
 *   <li>the camera is snapped back to its captured facing only when it drifts past {@link #LOOK_SNAP_DEG} —
 *       position is never pinned;</li>
 *   <li>walk/steer input is neutralised by extreme Slowness and jump by a negative Jump Boost.</li>
 * </ul>
 * Refresh-not-stack per victim; a static registry (the sibling-service lifecycle) with a {@link #clearAll}
 * disable teardown and a {@link #lockCount} test seam.
 */
public final class ControlLockService {

    /** The movement suppressor: extreme Slowness zeroes the victim's walk/steer INPUT for the lock. */
    private static final int SLOW_AMPLIFIER = 250;
    /** The jump suppressor: a NEGATIVE Jump Boost so pressing jump can't leave the ground — negative on both the
     * byte-amplifier legacy tree and the int-amplifier modern range (positive would wrap or over-boost). */
    private static final int JUMP_LOCK_AMPLIFIER = -10;
    /** Degrees the view may drift before the lock snaps it back — small enough to feel pinned, large enough
     * that a still (non-turning) victim is never teleported. */
    private static final float LOOK_SNAP_DEG = 6.0f;
    // The player ballistic model, re-integrated each lock tick so the tracked arc matches vanilla free-fall.
    private static final double ARC_GRAVITY = 0.08;
    private static final double ARC_DRAG_Y = 0.98;
    private static final double ARC_DRAG_XZ = 0.91;

    private static final Map<UUID, TaskHandle> HOLDS = new ConcurrentHashMap<>(); // victim → lock task

    private final TriggerDispatch dispatch;
    private final int slowId; // interned SLOWNESS (movement suppressor) or -1 (graceful skip)
    private final int jumpId; // interned JUMP_BOOST (jump suppressor, applied at a negative level) or -1

    public ControlLockService(TriggerDispatch dispatch, PlatformResolvers resolvers) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        Objects.requireNonNull(resolvers, "resolvers");
        this.slowId = resolvers.potionEffect("SLOW").orElse(-1); // the era Aliases chain maps SLOW→SLOWNESS on 1.20.5+
        this.jumpId = resolvers.potionEffect("JUMP").orElse(-1); // …and JUMP→JUMP_BOOST
    }

    /**
     * Arm the control-lock on {@code victim}: after {@code delay} ticks (letting the knock land) lock control for
     * {@code lock} ticks. Runs on the victim's own scheduler; the caller has already applied its own impulse.
     */
    public void arm(LivingEntity victim, int delay, int lock) {
        UUID vid = victim.getUniqueId();
        Scheduling.onEntityLater(victim, Math.max(0, delay), () -> {
            if (!victim.isValid()) {
                return;
            }
            TaskHandle old = HOLDS.remove(vid);
            if (old != null) {
                old.cancel(); // refresh-not-stack: a second lock mid-window replaces the task
            }
            if (slowId >= 0) {
                dispatch.potion(victim, slowId, SLOW_AMPLIFIER, lock); // walk/steer input → ~0
            }
            if (jumpId >= 0) {
                dispatch.potion(victim, jumpId, JUMP_LOCK_AMPLIFIER, lock); // negative jump boost → can't jump away
            }
            Location start = victim.getLocation(); // captured post-knock — only the FACING is held, never position
            float heldYaw = start.getYaw();
            float heldPitch = start.getPitch();
            Vector[] arc = {victim.getVelocity()}; // the tracked ballistic velocity, seeded from the live knock
            long deadline = lock;
            long[] t = {0};
            TaskHandle[] h = new TaskHandle[1];
            h[0] = Scheduling.repeatingEntity(victim, 1L, 1L, () -> {
                if (!victim.isValid() || ++t[0] > deadline) { // quit/death/expiry → release
                    HOLDS.remove(vid);
                    if (h[0] != null) {
                        h[0].cancel();
                    }
                    return;
                }
                // Advance the tracked arc by the player physics model FIRST, so we always hold this instant's
                // expected velocity independent of any teleport that may reset it below.
                arc[0] = advanceArc(arc[0]);
                // Camera lock: snap the view back to the held facing ONLY on a real drift (a mouse-turn), at the
                // victim's LIVE position (never a position pin). On a snap the era teleport leaf resets the
                // player's motion — harmless here because we re-assert the tracked arc immediately after.
                Location now = victim.getLocation();
                if (angleDrift(now.getYaw(), heldYaw) > LOOK_SNAP_DEG
                        || angleDrift(now.getPitch(), heldPitch) > LOOK_SNAP_DEG) {
                    Location look = now.clone();
                    look.setYaw(heldYaw);
                    look.setPitch(heldPitch);
                    dispatch.teleportEntity(victim, look);
                }
                // ALWAYS re-assert the tracked arc so the fall stays a smooth downward curve no matter how much
                // the victim spams the camera (each snap's velocity reset is corrected the same tick / the next).
                victim.setVelocity(arc[0].clone());
            });
            HOLDS.put(vid, h[0]);
        });
    }

    /** Disable stop ("control locks"): release every locked victim. */
    public static void clearAll() {
        for (TaskHandle hold : HOLDS.values()) {
            hold.cancel();
        }
        HOLDS.clear();
    }

    /** Test seam: the number of locked victims. */
    static int lockCount() {
        return HOLDS.size();
    }

    /** One tick of the player ballistic model: horizontal drag, vertical gravity then drag — a smooth free-fall arc. */
    private static Vector advanceArc(Vector v) {
        double vy = (v.getY() - ARC_GRAVITY) * ARC_DRAG_Y;
        return new Vector(v.getX() * ARC_DRAG_XZ, vy, v.getZ() * ARC_DRAG_XZ);
    }

    /** Absolute wrapped angular difference in degrees (yaw wraps at ±180). */
    private static float angleDrift(float a, float b) {
        float d = Math.abs(a - b) % 360.0f;
        return d > 180.0f ? 360.0f - d : d;
    }
}
