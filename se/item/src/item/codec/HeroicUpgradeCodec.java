package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / detects a heroic UPGRADE consumable (§F): a PDC {@code BYTE} flag under
 * {@link ItemKeys#heroicUpgrade()}, off the combat hot path.
 */
public final class HeroicUpgradeCodec {

    private final String key;
    private final ItemStateStore store;

    public HeroicUpgradeCodec(String key, ItemStateStore store) {
        this.key = key;
        this.store = store;
    }

    public boolean isUpgrade(ItemStack stack) {
        return store.hasByte(stack, key);
    }

    public void mark(ItemStack stack) {
        store.setByte(stack, key, true);
    }
}
