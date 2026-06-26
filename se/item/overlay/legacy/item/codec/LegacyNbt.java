package item.codec;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shared 1.8.9 ({@code v1_8_R3}) NBT access for the legacy {@link ItemBlobStore}/{@link ItemFlagStore}
 * overlay impls. All StarEnchants state lives under a single root compound ({@value #ROOT}) inside the
 * meta's {@code unhandledTags} map — the very {@link ItemMeta} the codecs already read and write.
 *
 * <p>Storing through the meta (rather than the item's NMS handle) is the load-bearing choice: 1.8's
 * {@code setItemMeta} <em>serialises</em> {@code unhandledTags} back onto the item instead of dropping
 * unknown NBT, so the modern codecs' read → {@code setItemMeta(lore)} → write flow is preserved on 1.8
 * with no special ordering. This is the structural fix for the §6/R5 "setItemMeta NBT-wipe" trap.
 *
 * <p>{@code CraftMetaItem} (which declares {@code unhandledTags}) is package-private in v1_8_R3, so the
 * field is reached reflectively by walking the concrete meta's superclass chain — resolved once and cached.
 */
final class LegacyNbt {

    static final String ROOT = "StarEnchants";

    private static volatile Field unhandledField;

    private LegacyNbt() {
    }

    /** The mutable {@code unhandledTags} map of {@code meta}, or {@code null} if it is not a CraftMetaItem. */
    @SuppressWarnings("unchecked")
    static Map<String, NBTBase> unhandled(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        Field field = field(meta);
        if (field == null) {
            return null;
        }
        try {
            return (Map<String, NBTBase>) field.get(meta);
        } catch (IllegalAccessException unreachable) {
            return null; // setAccessible(true) succeeded below
        }
    }

    private static Field field(ItemMeta meta) {
        Field cached = unhandledField;
        if (cached != null) {
            return cached;
        }
        for (Class<?> type = meta.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField("unhandledTags");
                found.setAccessible(true);
                unhandledField = found; // declared on CraftMetaItem; stable for the JVM's lifetime
                return found;
            } catch (NoSuchFieldException keepWalking) {
                // unhandledTags is on the CraftMetaItem superclass, not the concrete meta subtype
            }
        }
        return null;
    }

    /** The SE root compound for reading, or {@code null} when the item carries none. */
    static NBTTagCompound rootForRead(ItemMeta meta) {
        Map<String, NBTBase> tags = unhandled(meta);
        if (tags == null) {
            return null;
        }
        NBTBase root = tags.get(ROOT);
        return root instanceof NBTTagCompound ? (NBTTagCompound) root : null;
    }

    /** The existing SE root compound, or a fresh one to populate. */
    static NBTTagCompound rootForWrite(Map<String, NBTBase> tags) {
        NBTBase existing = tags.get(ROOT);
        return existing instanceof NBTTagCompound ? (NBTTagCompound) existing : new NBTTagCompound();
    }

    /** Re-attach (or drop, when empty) the root compound and serialise it back onto {@code stack}. */
    static void commit(ItemStack stack, ItemMeta meta, Map<String, NBTBase> tags, NBTTagCompound root) {
        if (root.isEmpty()) {
            tags.remove(ROOT);
        } else {
            tags.put(ROOT, root);
        }
        stack.setItemMeta(meta);
    }
}
