package item.worn;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) impl of {@link EquipSource} — the era-exclusive {@code overlay/legacy} equipment read (ADR-0044;
 * §3.3). 1.8 has no off-hand slot, so this reads the four armour pieces plus the single held item via
 * {@code getItemInHand()} ({@code getItemInMainHand} does not exist on 1.8.9 — precisely why this read is a seam).
 */
public final class LegacyEquipSource implements EquipSource {

    @Override
    public ItemStack[] snapshot(LivingEntity entity) {
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
