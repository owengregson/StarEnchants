package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects an UNOPENED / RANDOMIZED book (§I), storing the tier it is scoped to as a PDC
 * {@code STRING} under {@link ItemKeys#unopened()}, off the combat hot path.
 */
public final class UnopenedBookCodec {

    private final NamespacedKey key;

    public UnopenedBookCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isUnopened(ItemStack stack) {
        return tierOf(stack) != null;
    }

    /** The tier {@code stack} is scoped to, or {@code null} if it is not an unopened book. */
    public String tierOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? null : raw;
    }

    public void mark(ItemStack stack, String tier) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tier);
        stack.setItemMeta(meta);
    }
}
