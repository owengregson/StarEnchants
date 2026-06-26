package feature.combat;

import engine.stores.KnockbackControlStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) KNOCKBACK_CONTROL applier (docs/legacy-1.8.9-codeshare-design.md §6, Item 3). 1.8 has no Paper
 * knockback event, so the control is applied at the NMS source: {@link EntityDamageByEntityEvent} fires BEFORE
 * vanilla applies the hit's knockback in the same NMS call, so setting the victim's knockback-RESISTANCE
 * attribute ({@code GenericAttributes.c}) there scales the upcoming knockback exactly — resistance {@code r}
 * scales knockback by {@code 1 − r}, so {@code r = 1 − multiplier} is a precise cancel ({@code m ≤ 0} → r = 1)
 * or reduce. The original resistance is restored next tick (so only this hit is affected). Amplify
 * ({@code m > 1}) cannot be done by resistance, so it falls back to a best-effort next-tick velocity scale.
 *
 * <p>Reflective v1_8_R3 NMS (the tester compiles against the floor, so the constants are reached by name); this
 * class is registered only on the 1.8 lane (KnockbackListener.register, guarded by the v1_8_R3 probe), so the
 * reflection always resolves. Main-thread only — the 1.8 lane is never Folia.
 */
final class LegacyKnockbackListener implements Listener {

    private final KnockbackControlStore store;
    private final LongSupplier nowTicks;
    /** Victims whose resistance is temporarily overridden → their TRUE resistance, restored next tick. */
    private final Map<UUID, Double> savedResistance = new ConcurrentHashMap<>();

    LegacyKnockbackListener(KnockbackControlStore store, LongSupplier nowTicks) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double multiplier = store.multiplier(victim.getUniqueId(), nowTicks.getAsLong());
        if (Double.isNaN(multiplier)) {
            return; // no active KNOCKBACK_CONTROL flag for this entity
        }
        if (multiplier > 1.0) {
            amplifyNextTick(victim, multiplier); // resistance can only reduce — scale up via velocity instead
            return;
        }
        // m ≤ 0 → r = 1 (full cancel); 0 < m ≤ 1 → r = 1 − m (scale the knockback by m). Exact, at the source.
        applyResistance(victim, clamp01(1.0 - multiplier));
    }

    private void applyResistance(LivingEntity victim, double resistance) {
        Object attr = resistanceInstance(victim);
        if (attr == null) {
            return; // reflection unavailable — KNOCKBACK_CONTROL silently inert rather than throwing on a hit
        }
        try {
            UUID id = victim.getUniqueId();
            double original = (double) GET_VALUE.invoke(attr);
            // Capture the TRUE resistance + schedule the restore only on the first hit of a window; later hits
            // in the same window just re-set the override (the single scheduled restore returns the true value).
            if (savedResistance.putIfAbsent(id, original) == null) {
                Scheduling.onEntityLater(victim, 1L, () -> restore(victim));
            }
            SET_VALUE.invoke(attr, resistance);
        } catch (ReflectiveOperationException ignored) {
            // best-effort; a missing accessor would be a 1.8 mapping surprise, not worth crashing a hit
        }
    }

    private void restore(LivingEntity victim) {
        Double original = savedResistance.remove(victim.getUniqueId());
        if (original == null) {
            return;
        }
        Object attr = resistanceInstance(victim);
        if (attr == null) {
            return;
        }
        try {
            SET_VALUE.invoke(attr, original.doubleValue());
        } catch (ReflectiveOperationException ignored) {
            // best-effort restore
        }
    }

    private static void amplifyNextTick(LivingEntity victim, double multiplier) {
        Vector pre = victim.getVelocity();
        // The hit's knockback is applied after this event returns; next tick scale the applied delta by m.
        Scheduling.onEntityLater(victim, 1L, () -> {
            Vector post = victim.getVelocity();
            victim.setVelocity(pre.clone().add(post.clone().subtract(pre).multiply(multiplier)));
        });
    }

    /** The victim's NMS knockback-resistance {@code AttributeInstance}, or {@code null} if reflection fails. */
    private static Object resistanceInstance(LivingEntity victim) {
        if (!REFLECTION_OK) {
            return null;
        }
        try {
            Object handle = GET_HANDLE.invoke(victim); // CraftLivingEntity#getHandle → EntityLiving
            return GET_ATTRIBUTE_INSTANCE.invoke(handle, KNOCKBACK_RESISTANCE);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    // ── Reflective v1_8_R3 handles, resolved once (the legacy lane's only NMS surface for §C) ──
    private static final boolean REFLECTION_OK;
    private static final Method GET_HANDLE;
    private static final Method GET_ATTRIBUTE_INSTANCE;
    private static final Method GET_VALUE;
    private static final Method SET_VALUE;
    private static final Object KNOCKBACK_RESISTANCE;

    static {
        Method getHandle = null;
        Method getAttributeInstance = null;
        Method getValue = null;
        Method setValue = null;
        Object knockbackResistance = null;
        boolean ok = false;
        try {
            String nms = "net.minecraft.server.v1_8_R3.";
            Class<?> craftLiving = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity");
            Class<?> iAttribute = Class.forName(nms + "IAttribute");
            Class<?> entityLiving = Class.forName(nms + "EntityLiving");
            Class<?> attributeInstance = Class.forName(nms + "AttributeInstance");
            getHandle = craftLiving.getMethod("getHandle");
            getAttributeInstance = entityLiving.getMethod("getAttributeInstance", iAttribute);
            getValue = attributeInstance.getMethod("getValue");
            setValue = attributeInstance.getMethod("setValue", double.class);
            Field c = Class.forName(nms + "GenericAttributes").getField("c"); // knockback resistance
            knockbackResistance = c.get(null);
            ok = true;
        } catch (ReflectiveOperationException notLegacy) {
            // not the v1_8_R3 lane (this class is only registered there anyway) — leave the applier inert
        }
        REFLECTION_OK = ok;
        GET_HANDLE = getHandle;
        GET_ATTRIBUTE_INSTANCE = getAttributeInstance;
        GET_VALUE = getValue;
        SET_VALUE = setValue;
        KNOCKBACK_RESISTANCE = knockbackResistance;
    }
}
