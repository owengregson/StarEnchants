package item.head;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * The 1.8.9 {@link ItemBytes}: the vanilla item-NBT round-trip through the gzip stream codec over
 * {@code CraftItemStack.asNMSCopy(...).save(...)} — the exact shape items take on the 1.8 wire. Corruption
 * degrades to {@code null}, never a throw (the caller treats null as detection-only; no logger here — the
 * legacy overlay is JDK8-source and this is a cold, per-item cosmetic path).
 */
public final class LegacyItemBytes implements ItemBytes {

    @Override
    public byte[] serialize(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        try {
            net.minecraft.server.v1_8_R3.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (nms == null) {
                return null;
            }
            NBTTagCompound tag = nms.save(new NBTTagCompound());
            ByteArrayOutputStream out = new ByteArrayOutputStream(256);
            NBTCompressedStreamTools.a(tag, out);
            return out.toByteArray();
        } catch (Throwable failure) {
            return null;
        }
    }

    @Override
    public ItemStack deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            NBTTagCompound tag = NBTCompressedStreamTools.a(new ByteArrayInputStream(bytes));
            net.minecraft.server.v1_8_R3.ItemStack nms = net.minecraft.server.v1_8_R3.ItemStack.createStack(tag);
            return nms == null ? null : CraftItemStack.asBukkitCopy(nms);
        } catch (Throwable corrupt) {
            return null;
        }
    }
}
