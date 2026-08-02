package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * The per-item lifetime count of holy white scrolls an item has SPENT saving itself (§I corruption). A holy
 * scroll's keep-marker ({@link AppliedSlot#HOLY}) is one-shot; when a death actually consumes it, this counter
 * bumps — so it records protections DELIVERED, not protections applied. An item that never dies never corrupts.
 *
 * <p>The count drives the corruption lore line ({@link item.render.CorruptionLore}) and the apply refusal once
 * the configured maximum is reached. Its own PDC {@code INTEGER}, separate from the {@link CombatState} blob,
 * on the {@link TrakCodec} precedent: a death-time bump must not thrash the content-hash {@link ItemView} cache.
 */
public final class HolyProtectionCodec {

    private final String key;
    private final ItemStateStore store;

    public HolyProtectionCodec(String key, ItemStateStore store) {
        this.key = key;
        this.store = store;
    }

    /** How many holy white scrolls {@code stack} has spent saving itself (0 if none). */
    public int count(ItemStack stack) {
        return Math.max(0, store.readInt(stack, key, 0));
    }

    /** Bump the lifetime count by one and return the new value. */
    public int increment(ItemStack stack) {
        int next = count(stack) + 1;
        store.writeInt(stack, key, next);
        return next;
    }
}
