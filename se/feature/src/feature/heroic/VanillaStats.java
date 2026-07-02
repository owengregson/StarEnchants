package feature.heroic;

import org.bukkit.inventory.ItemStack;

/**
 * The version edge for writing a heroic piece's REAL vanilla diamond stats (ADR-0031/0044;
 * docs/legacy-1.8.9-codeshare-design.md §4): 1.8.9 has no Bukkit attribute API and no custom max-durability
 * component, so it keeps the plugin's own combat-maths diamond-equivalence instead. This is a DEFAULT+OVR seam:
 * the shared default {@link #NONE} returns {@code false} (keep the plugin-maths fold), and the modern override
 * {@code ModernVanillaStats} ({@code overlay/modern}) writes real attributes. Injected into {@code HeroicService}.
 */
public interface VanillaStats {

    /** Write real vanilla diamond stats onto {@code stack}; {@code true} iff real attributes were written for this piece. */
    boolean apply(ItemStack stack, boolean weapon);

    /** 1.8.9 (and any era with no attribute API): no real attributes — keep the plugin-maths flat fold. */
    VanillaStats NONE = (stack, weapon) -> false;
}
