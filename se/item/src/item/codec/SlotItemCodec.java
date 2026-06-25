package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a SLOT EXPANDER orb (§H), storing the {@code +N} it grants as a PDC {@code INTEGER}
 * under {@link ItemKeys#slotItem()}. The granted slots themselves persist in the gear's
 * {@link CombatState#added()} field, not here.
 */
public final class SlotItemCodec {

    private final NamespacedKey key;

    public SlotItemCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isSlotItem(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
    }

    /** The {@code +N} slots {@code stack} grants, or {@code 0} if it is not a slot item. */
    public int amountOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer amount = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return amount == null ? 0 : Math.max(0, amount);
    }

    /** Stamp the slot-item marker onto {@code stack}, granting {@code amount} extra slots (clamped &ge; 1). */
    public void mark(ItemStack stack, int amount) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.max(1, amount));
        stack.setItemMeta(meta);
    }
}
