package feature.reforge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) weapon-swing reader (ADR-0071 Javelin). 1.8 has no player-side weapon attribute, so a swing
 * is the held material's vanilla melee damage from {@link WeaponDamage#legacyMaterialDamage}. The main-hand
 * read is null-guarded (the 1.8 empty-hand NPE trap) — an empty hand is the 1-damage fist.
 */
public final class LegacyWeaponDamage implements WeaponDamage {

    public LegacyWeaponDamage() {
    }

    @Override
    public double swingDamage(Player holder) {
        ItemStack inHand = holder.getInventory().getItemInHand(); // 1.8: main hand only
        if (inHand == null) {
            return 1.0; // empty hand — the fist base
        }
        return WeaponDamage.legacyMaterialDamage(inHand.getType().name());
    }
}
