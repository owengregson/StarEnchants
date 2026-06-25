package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a heroic UPGRADE consumable (§F): a PDC {@code BYTE} flag under
 * {@link ItemKeys#heroicUpgrade()}, off the combat hot path.
 */
public final class HeroicUpgradeCodec {

    private final NamespacedKey key;

    public HeroicUpgradeCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isUpgrade(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void mark(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
