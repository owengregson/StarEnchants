package item.head;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The modern (1.17.1 → 26.1.x) {@link EquipmentRepaint}: {@code Player.sendEquipmentChange} resolved ONCE by
 * name — a Bukkit API method (mapping-flip immune, no remapper) that is absent from the 1.17.1 compile floor,
 * so it cannot be a direct call. Absent at runtime (1.17.1 exactly) → {@link #helmet} is false forever, the
 * ADR-0053 §4 recorded degrade.
 */
public final class ModernEquipmentRepaint implements EquipmentRepaint {

    private static final Logger LOG = System.getLogger("StarEnchants.EquipmentRepaint");

    private static final MethodHandle SEND_EQUIPMENT_CHANGE = probe();

    /** Log-once latch for an invoke-time failure; sends keep being attempted (the fault may be per-recipient). */
    private static volatile boolean sendFailureLogged;

    @Override
    public boolean helmet(Player recipient, Player wearer, ItemStack shown) {
        MethodHandle send = SEND_EQUIPMENT_CHANGE;
        if (send == null) {
            return false;
        }
        try {
            send.invokeExact(recipient, (LivingEntity) wearer, EquipmentSlot.HEAD, shown);
            return true;
        } catch (Throwable failure) {
            if (!sendFailureLogged) {
                sendFailureLogged = true;
                LOG.log(Level.WARNING, "Player.sendEquipmentChange failed — a worn mask repaint was dropped",
                        failure);
            }
            return false;
        }
    }

    private static MethodHandle probe() {
        try {
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(Player.class, "sendEquipmentChange",
                    MethodType.methodType(void.class, LivingEntity.class, EquipmentSlot.class, ItemStack.class));
            LOG.log(Level.DEBUG, "Player.sendEquipmentChange present — the worn mask repaint is active");
            return handle;
        } catch (NoSuchMethodException | IllegalAccessException absent) {
            // 1.17.1 exactly: masks still grant abilities; the visual override is the recorded degrade (ADR-0053 §4).
            LOG.log(Level.DEBUG, "Player.sendEquipmentChange absent (1.17.1) — the worn mask repaint is inert");
            return null;
        }
    }
}
