package item.head;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Client-only worn-equipment repaint (ADR-0053 masks) — an ADR-0044 era seam: the modern impl resolves Paper's
 * {@code Player.sendEquipmentChange} by name (a Bukkit API method absent from the 1.17.1 compile floor), the 1.8
 * impl sends a direct-NMS {@code PacketPlayOutEntityEquipment}. A pure packet write — no inventory/world mutation;
 * {@code shown} may be the TRUE helmet, so the same call restores reality. Must run on the RECIPIENT's owning
 * region thread (the connection write); callers hop via {@code Scheduling.onEntity(recipient, ...)}.
 */
public interface EquipmentRepaint {

    /** The inert default (tests, 1.17.1): the mask still grants abilities, the visual override degrades. */
    EquipmentRepaint NONE = (recipient, wearer, shown) -> false;

    /** Client-only HEAD-slot override of {@code wearer} as seen by {@code recipient}; false = unsupported. */
    boolean helmet(Player recipient, Player wearer, ItemStack shown);
}
