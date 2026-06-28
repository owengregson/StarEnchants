package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / detects a SLOT EXPANDER orb (§H), storing the {@code +N} it grants as a PDC {@code INTEGER}
 * under {@link ItemKeys#slotItem()} and its per-item rolled success chance under
 * {@link ItemKeys#slotSuccess()}. The granted slots themselves persist in the gear's
 * {@link CombatState#added()} field, not here.
 */
public final class SlotItemCodec {

    private final String key;
    private final String successKey;

    public SlotItemCodec(String key, String successKey) {
        this.key = key;
        this.successKey = successKey;
    }

    public boolean isSlotItem(ItemStack stack) {
        return ItemFlagStore.hasInt(stack, key);
    }

    /** The {@code +N} slots {@code stack} grants, or {@code 0} if it is not a slot item. */
    public int amountOf(ItemStack stack) {
        return Math.max(0, ItemFlagStore.readInt(stack, key, 0));
    }

    /** The orb's rolled apply success chance (0–100), or {@code 100} when absent (a pre-success-roll orb). */
    public int successOf(ItemStack stack) {
        return Math.max(0, Math.min(100, ItemFlagStore.readInt(stack, successKey, 100)));
    }

    /**
     * Stamp the slot-item marker onto {@code stack}, granting {@code amount} extra slots (clamped &ge; 1) at a
     * rolled {@code success} chance (clamped {@code [0, 100]}).
     */
    public void mark(ItemStack stack, int amount, int success) {
        ItemFlagStore.writeInt(stack, key, Math.max(1, amount));
        ItemFlagStore.writeInt(stack, successKey, Math.max(0, Math.min(100, success)));
    }
}
