package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

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
        return ItemFlagStore.hasByte(stack, key);
    }

    public void mark(ItemStack stack) {
        ItemFlagStore.setByte(stack, key, true);
    }
}
