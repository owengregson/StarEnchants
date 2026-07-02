package item.worn;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Modern (1.9+) impl of {@link EquipSource} — the era-exclusive {@code overlay/modern} equipment read (ADR-0044;
 * §3.3). Reads the four armour pieces plus main hand and off-hand; off-hand shields/totems carry enchants too.
 * {@code getItemInOffHand} does not exist on 1.8.9, which is why this read is a seam.
 */
public final class ModernEquipSource implements EquipSource {

    @Override
    public ItemStack[] snapshot(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return null;
        }
        ItemStack[] armor = equipment.getArmorContents(); // 4 slots; null for some non-player entities
        ItemStack[] out = new ItemStack[6];
        if (armor != null) {
            System.arraycopy(armor, 0, out, 0, Math.min(4, armor.length));
        }
        out[4] = equipment.getItemInMainHand();
        out[5] = equipment.getItemInOffHand(); // off-hand shields/totems carry enchants too
        return out;
    }
}
