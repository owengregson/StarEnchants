package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * The single exclusive APPLIED-UTILITY slot on a piece of gear (§I). An item may carry at most ONE of the
 * mutually-exclusive applied items at a time — a white scroll guard, a holy white scroll keep-marker, or one
 * of the three trak gems. The occupant's kind is stored as a PDC {@code STRING} under
 * {@link ItemKeys#appliedSlot()}, off the combat hot path.
 *
 * <p>Each applier checks {@link #canApply} before consuming, {@link #occupy}s the slot on success, and
 * {@link #release}s it when its marker is consumed (the white scroll on a failed enchant save, the holy scroll
 * on death). The trak gems never release — once applied they ride the item for its lifetime.
 */
public final class AppliedSlot {

    public static final String WHITE_SCROLL = "white-scroll";
    public static final String HOLY = "holy";
    public static final String BLOCKTRAK = "blocktrak";
    public static final String MOBTRAK = "mobtrak";
    public static final String SOULTRAK = "soultrak";

    private final String key;

    public AppliedSlot(String key) {
        this.key = key;
    }

    /** The kind currently occupying the slot on {@code stack}, or {@code null} if the slot is empty. */
    public String occupant(ItemStack stack) {
        String raw = ItemBlobStore.read(stack, key);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** Whether {@code kind} may be applied to {@code stack}: the slot is empty, or already holds {@code kind}. */
    public boolean canApply(ItemStack stack, String kind) {
        String cur = occupant(stack);
        return cur == null || cur.equals(kind);
    }

    /** Whether {@code stack} already carries {@code kind} (a no-op re-apply guard, distinct from a clash). */
    public boolean holds(ItemStack stack, String kind) {
        return kind.equals(occupant(stack));
    }

    public void occupy(ItemStack stack, String kind) {
        ItemBlobStore.write(stack, key, kind);
    }

    public void release(ItemStack stack) {
        ItemBlobStore.write(stack, key, null);
    }
}
