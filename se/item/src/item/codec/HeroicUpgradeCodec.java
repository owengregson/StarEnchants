package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a heroic UPGRADE item (docs/v3-directives.md §F) — a one-shot consumable dragged onto
 * gear to attempt a heroic upgrade. A simple PDC {@code BYTE} flag under {@link ItemKeys#heroicUpgrade()},
 * separate from the combat blob (identity, never on the hot path). Reading is null-safe.
 */
public final class HeroicUpgradeCodec {

    private final NamespacedKey key;

    public HeroicUpgradeCodec(NamespacedKey key) {
        this.key = key;
    }

    /** Whether {@code stack} is a heroic upgrade item. */
    public boolean isUpgrade(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Stamp the heroic-upgrade marker onto {@code stack}. */
    public void mark(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
