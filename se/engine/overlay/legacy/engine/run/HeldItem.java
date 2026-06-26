package engine.run;

import org.bukkit.entity.LivingEntity;

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
        return entity.getEquipment() == null ? null
                : entity.getEquipment().getItemInHand().getType().name();
    }
}
