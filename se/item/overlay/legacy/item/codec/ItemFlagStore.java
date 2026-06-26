package item.codec;

import java.util.Map;
import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Legacy (1.8.9 / {@code v1_8_R3}) impl of the BYTE/INTEGER marker seam (§4.2) — same-FQN counterpart to
 * the modern PDC impl in {@code overlay/modern}. Markers ride the SE root compound in the meta's
 * {@code unhandledTags} (see {@link LegacyNbt}); each logical key only ever holds one type, so
 * {@code hasKey} alone distinguishes presence.
 */
public final class ItemFlagStore {

    private ItemFlagStore() {
    }

    /** Whether {@code stack} carries the BYTE flag under {@code logicalKey}. */
    public static boolean hasByte(ItemStack stack, String logicalKey) {
        return has(stack, logicalKey);
    }

    /** Sets ({@code 1}) or clears the BYTE flag under {@code logicalKey}. No-op if the item has no meta. */
    public static void setByte(ItemStack stack, String logicalKey, boolean set) {
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

    /** Whether {@code stack} carries an INTEGER under {@code logicalKey}. */
    public static boolean hasInt(ItemStack stack, String logicalKey) {
        return has(stack, logicalKey);
    }

    /** The INTEGER under {@code logicalKey}, or {@code dflt} if absent. */
    public static int readInt(ItemStack stack, String logicalKey, int dflt) {
        if (stack == null || !stack.hasItemMeta()) {
            return dflt;
        }
        NBTTagCompound root = LegacyNbt.rootForRead(stack.getItemMeta());
        return root != null && root.hasKey(logicalKey) ? root.getInt(logicalKey) : dflt;
    }

    /** Writes {@code value} as an INTEGER under {@code logicalKey}. No-op if the item has no meta. */
    public static void writeInt(ItemStack stack, String logicalKey, int value) {
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
