package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

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
        return ItemFlagStore.hasInt(stack, key);
    }

    /** The {@code +N} slots {@code stack} grants, or {@code 0} if it is not a slot item. */
    public int amountOf(ItemStack stack) {
        return Math.max(0, ItemFlagStore.readInt(stack, key, 0));
    }

    /** Stamp the slot-item marker onto {@code stack}, granting {@code amount} extra slots (clamped &ge; 1). */
    public void mark(ItemStack stack, int amount) {
        ItemFlagStore.writeInt(stack, key, Math.max(1, amount));
    }
}
