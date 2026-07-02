package item.codec;

import org.bukkit.inventory.ItemStack;
import schema.spec.Ranges;

/**
 * Marks / detects a SLOT EXPANDER orb (§H), storing the {@code +N} it grants as a PDC {@code INTEGER}
 * under {@link ItemKeys#slotItem()} and its per-item rolled success chance under
 * {@link ItemKeys#slotSuccess()}. The granted slots themselves persist in the gear's
 * {@link CombatState#added()} field, not here.
 */
public final class SlotItemCodec {

    private final String key;
    private final String successKey;
    private final ItemStateStore store;

    public SlotItemCodec(String key, String successKey, ItemStateStore store) {
        this.key = key;
        this.successKey = successKey;
        this.store = store;
    }

    public boolean isSlotItem(ItemStack stack) {
        return store.hasInt(stack, key);
    }

    /** The {@code +N} slots {@code stack} grants, or {@code 0} if it is not a slot item. */
    public int amountOf(ItemStack stack) {
        return Math.max(0, store.readInt(stack, key, 0));
    }

    /** The orb's rolled apply success chance (0–100), or {@code 100} when absent (a pre-success-roll orb). */
    public int successOf(ItemStack stack) {
        return Ranges.clampPercent(store.readInt(stack, successKey, 100));
    }

    /**
     * Stamp the slot-item marker onto {@code stack}, granting {@code amount} extra slots (clamped &ge; 1) at a
     * rolled {@code success} chance (clamped {@code [0, 100]}).
     */
    public void mark(ItemStack stack, int amount, int success) {
        store.writeInt(stack, key, Math.max(1, amount));
        store.writeInt(stack, successKey, Ranges.clampPercent(success));
    }
}
