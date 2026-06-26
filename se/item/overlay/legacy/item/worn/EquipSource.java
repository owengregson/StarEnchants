package item.worn;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) equipment snapshot for {@link WornResolver}. 1.8 has no off-hand slot, so this reads the
 * four armour pieces plus the single held item via {@code getItemInHand()} ({@code getItemInMainHand} does
 * not exist on 1.8.9 — which is precisely why this read is a seam). Same-FQN counterpart to the
 * {@code overlay/modern} impl (docs/legacy-1.8.9-codeshare-design.md §3.3).
 */
public final class EquipSource {

    private EquipSource() {
    }

    /** {@code [helmet, chest, legs, boots, mainHand]} with {@code null} for empty slots, or {@code null} when there is no equipment. */
    public static ItemStack[] snapshot(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return null;
        }
        ItemStack[] armor = equipment.getArmorContents();
        ItemStack[] out = new ItemStack[5];
        if (armor != null) {
            System.arraycopy(armor, 0, out, 0, Math.min(4, armor.length));
        }
        out[4] = equipment.getItemInHand(); // 1.8 main hand
        return out;
    }
}
