package item.codec;

import java.util.Map;
import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Legacy (1.8.9 / {@code v1_8_R3}) impl of {@link ItemStateStore} — the era-exclusive {@code overlay/legacy}
 * half of the item-data seam (ADR-0044; §4.2, docs/legacy-1.8.9-codeshare-design.md §3.1). PDC does not exist
 * on 1.8.9, so state rides the SE root compound in the meta's {@code unhandledTags} (see {@link LegacyNbt}, whose
 * "meta-serialises-unhandledTags" trap fix is untouched). Each logical key only ever holds one type, so
 * {@code hasKey} alone distinguishes a byte/int marker's presence. The shared codecs call it by logical key,
 * blind to which storage form backs it.
 */
public final class NbtItemStateStore implements ItemStateStore {

    @Override
    public String read(ItemStack stack, String logicalKey) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        NBTTagCompound root = LegacyNbt.rootForRead(stack.getItemMeta());
        return root != null && root.hasKey(logicalKey) ? root.getString(logicalKey) : null;
    }

    @Override
    public void write(ItemStack stack, String logicalKey, String blob) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Map<String, NBTBase> tags = LegacyNbt.unhandled(meta);
        if (tags == null) {
            return;
        }
        NBTTagCompound root = LegacyNbt.rootForWrite(tags);
        if (blob == null) {
            root.remove(logicalKey);
        } else {
            root.setString(logicalKey, blob);
        }
        LegacyNbt.commit(stack, meta, tags, root);
    }

    @Override
    public boolean hasByte(ItemStack stack, String logicalKey) {
        return has(stack, logicalKey);
    }

    @Override
    public void setByte(ItemStack stack, String logicalKey, boolean set) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Map<String, NBTBase> tags = LegacyNbt.unhandled(meta);
        if (tags == null) {
            return;
        }
        NBTTagCompound root = LegacyNbt.rootForWrite(tags);
        if (set) {
            root.setByte(logicalKey, (byte) 1);
        } else {
            root.remove(logicalKey);
        }
        LegacyNbt.commit(stack, meta, tags, root);
    }

    @Override
    public boolean hasInt(ItemStack stack, String logicalKey) {
        return has(stack, logicalKey);
    }

    @Override
    public int readInt(ItemStack stack, String logicalKey, int dflt) {
        if (stack == null || !stack.hasItemMeta()) {
            return dflt;
        }
        NBTTagCompound root = LegacyNbt.rootForRead(stack.getItemMeta());
        return root != null && root.hasKey(logicalKey) ? root.getInt(logicalKey) : dflt;
    }

    @Override
    public void writeInt(ItemStack stack, String logicalKey, int value) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Map<String, NBTBase> tags = LegacyNbt.unhandled(meta);
        if (tags == null) {
            return;
        }
        NBTTagCompound root = LegacyNbt.rootForWrite(tags);
        root.setInt(logicalKey, value);
        LegacyNbt.commit(stack, meta, tags, root);
    }

    private static boolean has(ItemStack stack, String logicalKey) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        NBTTagCompound root = LegacyNbt.rootForRead(stack.getItemMeta());
        return root != null && root.hasKey(logicalKey);
    }
}
