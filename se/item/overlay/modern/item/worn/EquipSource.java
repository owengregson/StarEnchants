package item.worn;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Modern (1.9+) equipment snapshot for {@link WornResolver} — the one place the 1.9+ off-hand read lives,
 * pulled out of shared {@code main} so the flattening core stays version-agnostic
 * (docs/legacy-1.8.9-codeshare-design.md §3.3). Same-FQN counterpart to the {@code overlay/legacy}
 * main-hand-only impl; selected at build assembly, never probed.
 */
public final class EquipSource {

    private EquipSource() {
    }

    /**
     * {@code [helmet, chest, legs, boots, mainHand, offHand]} with {@code null} for empty slots, or
     * {@code null} when the entity has no equipment (a non-living/equipment-less mob).
     */
    public static ItemStack[] snapshot(LivingEntity entity) {
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
