package engine.run;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) main-hand read for {@link FactPopulator}. 1.8 has no off-hand and no
 * {@code getItemInMainHand()}; the single held item is {@code getItemInHand()}. Same-FQN counterpart to the
 * {@code overlay/modern} impl (docs/legacy-1.8.9-codeshare-design.md §3.3).
 */
public final class HeldItem {

    private HeldItem() {
    }

    /** The entity's held item type name, or {@code null} if it has no equipment. */
    @SuppressWarnings("deprecation") // getItemInHand is the 1.8 main-hand accessor (no getItemInMainHand on 1.8)
    public static String mainHandTypeName(LivingEntity entity) {
        if (entity.getEquipment() == null) {
            return null;
        }
        // 1.8 getItemInHand() returns null for an empty hand (modern getItemInMainHand() returns AIR); normalize
        // to AIR so the shared FactPopulator sees the SAME "AIR" name on both eras instead of NPE-ing the fact loop.
        ItemStack held = entity.getEquipment().getItemInHand();
        return held == null ? Material.AIR.name() : held.getType().name();
    }
}
