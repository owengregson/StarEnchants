package feature.trigger;

/**
 * The {@code %item.durabilitypercent%} formula, shared by both ITEM_DAMAGE sources so the fact means the same
 * thing on either lane — only the reads differ (modern {@code Damageable#getDamage} + the effective max,
 * 1.8 {@code ItemStack#getDurability} + the material max).
 */
public final class DurabilityPercent {

    private DurabilityPercent() {
    }

    /**
     * Remaining durability as a percent of {@code max} (0–100), given the item's current {@code damage} value.
     * {@link Double#NaN} when the item carries no durability bar — the context's "no reading" sentinel, since 0
     * would say "spent" and 100 "pristine" and neither is true of a stone block. Clamped, so a shrunken custom
     * max (or a transiently over-damaged stack) can never read outside the range an author gates on.
     */
    public static double of(int damage, int max) {
        if (max <= 0) {
            return Double.NaN;
        }
        int worn = Math.max(0, Math.min(damage, max));
        return 100.0 * (max - worn) / max;
    }
}
