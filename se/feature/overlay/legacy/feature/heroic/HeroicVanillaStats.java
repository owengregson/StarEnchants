package feature.heroic;

import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) heroic vanilla-stats writer (ADR-0031) — a no-op. 1.8.9 has no Bukkit attribute API and no
 * custom max-durability component, so a heroic piece keeps the plugin's own combat-maths diamond-equivalence
 * (the {@link HeroicDiamond} flat fold + the {@link HeroicDurabilityListener} poll-restore save) instead of real
 * vanilla attributes. Returns {@code false} so the caller keeps that plugin-maths path. Same FQN as the modern
 * counterpart, so the feature module's class set stays identical for the Multi-Release jar merge.
 */
public final class HeroicVanillaStats {

    private HeroicVanillaStats() {
    }

    /** Always {@code false} on 1.8.9: no real vanilla armour attributes are written — keep the plugin-maths fold. */
    public static boolean apply(ItemStack stack, boolean weapon) {
        return false;
    }
}
