package tester.suite;

import item.head.ModernHeadEquip;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tester.harness.Harness;

/**
 * The client-side helmet-wearability strip, live (1.8.4): a mask/pet head run through {@link ModernHeadEquip}
 * must carry an {@code equippable} component whose slot is NOT {@code HEAD}, so vanilla's own armour-slot check
 * refuses the helmet — masks activate applied ONTO a helmet, pets from the hotbar, neither belongs in the slot.
 * The component API is 1.21.2+; below it the strip is inert (the server-side {@code HeadEquipGuard} carries the
 * guard alone) and must simply never throw. Pure item-meta over this version's real ItemFactory — no player.
 */
public final class HeadEquipSuite implements Harness.Scenario {

    @Override
    public void accept(Harness h) {
        Method getEquippable = probeGetEquippable();
        if (getEquippable == null) {
            // pre-1.21.2: there is no equippable component to strip — unwearable() must be a no-op that never throws.
            h.expect("headequip.inertPre1212");
            h.guard("headequip.inertPre1212", HeadEquipSuite::strippedHead);
            return;
        }
        h.expect("headequip.helmetSlotNeutralised");
        h.guard("headequip.helmetSlotNeutralised", () -> {
            ItemStack head = strippedHead();
            ItemMeta meta = head.getItemMeta();
            if (meta == null) {
                throw new IllegalStateException("stripped head has no meta");
            }
            EquipmentSlot slot;
            try {
                Object component = getEquippable.invoke(meta);
                slot = (EquipmentSlot) component.getClass().getMethod("getSlot").invoke(component);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("equippable component read failed", e);
            }
            if (slot == EquipmentSlot.HEAD) {
                throw new IllegalStateException("head is still helmet-equippable — equippable slot is still HEAD");
            }
        });
    }

    /** A PLAYER_HEAD run through the modern strip — the mint-time path a pet/mask head takes. */
    private static ItemStack strippedHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        new ModernHeadEquip().unwearable(head);
        return head;
    }

    private static Method probeGetEquippable() {
        try {
            return ItemMeta.class.getMethod("getEquippable");
        } catch (NoSuchMethodException pre1212) {
            return null;
        }
    }
}
