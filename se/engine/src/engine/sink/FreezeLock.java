package engine.sink;

import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import org.bukkit.entity.Entity;

/**
 * Reflective probe over Paper's freeze-tick surface so shared code stays compilable on the 1.8.8
 * jar and the 1.17.1 floor: {@code lockFreezeTicks}/{@code isFreezeTickingLocked} exist from Paper
 * 1.18.2; {@code setFreezeTicks} from 1.17. Absent methods degrade to no-ops (the recorded
 * era/floor degrade). Probed once per JVM; cold-path only.
 */
public final class FreezeLock {

    private static final System.Logger LOG = System.getLogger("StarEnchants.FreezeLock");

    private static final Method LOCK = probe("lockFreezeTicks", boolean.class);
    private static final Method IS_LOCKED = probe("isFreezeTickingLocked");
    private static final Method SET_TICKS = probe("setFreezeTicks", int.class);

    private FreezeLock() {
    }

    private static Method probe(String name, Class<?>... params) {
        try {
            return Entity.class.getMethod(name, params);
        } catch (NoSuchMethodException absent) {
            return null; // pre-1.18.2 (lock pair) or 1.8.9 (all three) — the callers degrade
        }
    }

    /** Whether the Paper freeze-tick LOCK exists on this runtime (1.18.2+; guards decay AND the fire-clear). */
    public static boolean available() {
        return LOCK != null && IS_LOCKED != null;
    }

    /** Lock/unlock vanilla's freeze-tick writes on {@code entity}; a no-op when the API is absent. */
    public static void lock(Entity entity, boolean locked) {
        invoke(LOCK, entity, locked);
    }

    /** Whether {@code entity} is freeze-tick locked; {@code false} when the API is absent. */
    public static boolean isLocked(Entity entity) {
        if (IS_LOCKED == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(IS_LOCKED.invoke(entity));
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.WARNING, "isFreezeTickingLocked failed", e);
            return false;
        }
    }

    /** Set {@code entity}'s freeze ticks; a no-op when the API is absent (1.8.9). */
    public static void setTicks(Entity entity, int ticks) {
        invoke(SET_TICKS, entity, ticks);
    }

    private static void invoke(Method m, Entity entity, Object arg) {
        if (m == null) {
            return;
        }
        try {
            m.invoke(entity, arg);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.WARNING, m.getName() + " failed", e); // ADR-0042: throwable as the parameter
        }
    }
}
