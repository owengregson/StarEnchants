package item.codec;

import java.util.Map;
import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Legacy (1.8.9 / {@code v1_8_R3}) impl of the STRING-payload item-state seam (§4.2,
 * docs/legacy-1.8.9-codeshare-design.md §3.1) — the same-FQN counterpart to the modern PDC impl in
 * {@code overlay/modern}. PDC does not exist on 1.8.9, so state is stored as a string under the SE root
 * compound in the meta's {@code unhandledTags} (see {@link LegacyNbt}). Selected at build assembly; the
 * shared codecs call it by logical key, blind to which storage form backs it.
 */
public final class ItemBlobStore {

    private ItemBlobStore() {
    }

    /** The string stored under {@code logicalKey}, or {@code null} if {@code stack} carries none. */
    public static String read(ItemStack stack, String logicalKey) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        NBTTagCompound root = LegacyNbt.rootForRead(stack.getItemMeta());
        return root != null && root.hasKey(logicalKey) ? root.getString(logicalKey) : null;
    }

    /** Writes {@code blob} under {@code logicalKey}; a {@code null} blob clears it. No-op if the item has no meta. */
    public static void write(ItemStack stack, String logicalKey, String blob) {
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
}
