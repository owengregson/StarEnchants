package item.head;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The 1.8.9 {@link EquipmentRepaint}: a direct-NMS {@code PacketPlayOutEntityEquipment} on the recipient's
 * connection (the {@code LegacyDispatchSink.sendPacket} precedent). Main-thread only — the 1.8 lane is never
 * Folia. An AIR {@code shown} crosses {@code asNMSCopy} as null, which the 1.8 wire reads as an empty slot —
 * exactly the bare-head restore.
 */
public final class LegacyEquipmentRepaint implements EquipmentRepaint {

    /** Helmet in the 1.8 wire slot order: 0 held, 1 boots, 2 leggings, 3 chestplate, 4 helmet. */
    private static final int HELMET_SLOT = 4;

    @Override
    public boolean helmet(Player recipient, Player wearer, ItemStack shown) {
        if (!(recipient instanceof CraftPlayer) || !(wearer instanceof CraftPlayer)) {
            return false;
        }
        EntityPlayer handle = ((CraftPlayer) recipient).getHandle();
        if (handle.playerConnection == null) {
            return false;
        }
        handle.playerConnection.sendPacket(new PacketPlayOutEntityEquipment(
                ((CraftPlayer) wearer).getHandle().getId(), HELMET_SLOT, CraftItemStack.asNMSCopy(shown)));
        return true;
    }
}
