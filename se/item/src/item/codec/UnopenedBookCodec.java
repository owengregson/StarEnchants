package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects an UNOPENED / RANDOMIZED book (docs/v3-directives.md §I) and stores the rarity tier it
 * is scoped to. Right-clicking it yields a concrete enchant book of a random enchant from that tier. The
 * tier is a PDC {@code STRING} under {@link ItemKeys#unopened()}, separate from the combat blob (identity,
 * never on the hot path). Reading is null-safe.
 */
public final class UnopenedBookCodec {

    private final NamespacedKey key;

    public UnopenedBookCodec(NamespacedKey key) {
        this.key = key;
    }

    /** Whether {@code stack} is an unopened book. */
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

    /** Stamp the unopened-book marker onto {@code stack}, scoping it to {@code tier}. */
    public void mark(ItemStack stack, String tier) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tier);
        stack.setItemMeta(meta);
    }
}
