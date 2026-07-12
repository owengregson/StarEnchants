package feature.guard;

import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Denies equipping an SE cosmetic HEAD item (a mask or a pet — ADR-0052/0053, both {@code PLAYER_HEAD}s) into
 * the HELMET armour slot. A bare player head is natively helmet-wearable, but a mask activates only APPLIED ONTO
 * a helmet (its drag gesture) and a pet activates from the HOTBAR, so neither belongs in the helmet slot. This
 * cancels every INVENTORY route a head can reach the slot by: a direct place / hotbar number-key swap onto an
 * armour slot, a drag painted across it, and the shift-click auto-equip in the player's own inventory screen.
 *
 * <p>The two non-inventory routes are covered elsewhere: right-click auto-equip is already denied by
 * {@link VanillaGuardListener} (masks/pets are plugin items, so their vanilla item-use is suppressed), and
 * dispenser-equip is the modern-only {@code DispenseArmorGuard} seam ({@code BlockDispenseArmorEvent} is absent
 * on the 1.8.9 floor). On 1.21.2+ the client itself refuses the slot via the stripped {@code equippable}
 * component ({@code HeadEquip}); this guard is the sub-1.21.2 carrier AND the server-side belt-and-braces above.
 *
 * <p>Always-on (the {@link VanillaGuardListener} rule): keyed off the item identity, not a feature toggle, so a
 * mask/pet head is never wearable even with its family disabled. Cross-version: no 1.9+-only API (no off-hand,
 * no {@code getClickedInventory}); {@code getSlotType}/{@code getRawSlots} are floor-stable, so the one shared
 * class runs on 1.8.9 and the modern range alike. A head UNEQUIP (shift-click FROM the armour slot) is left
 * alone, so a head slotted before this guard shipped can still be taken off.
 */
public final class HeadEquipGuard implements Listener {

    /** The helmet cell's raw slot in the player's own inventory view — vanilla-stable 1.8.9 → 26.x (0=craft
     *  result, 1-4=craft grid, 5=helmet, 6-8=other armour, 9+=inventory). */
    private static final int HELMET_RAW_SLOT = 5;

    private final Predicate<ItemStack> isHeadItem; // null/AIR-safe (wrapped at the composition root)

    public HeadEquipGuard(Predicate<ItemStack> isHeadItem) {
        this.isHeadItem = Objects.requireNonNull(isHeadItem, "isHeadItem");
    }

    // HIGH so the cancel is authoritative among ordinary handlers; the MONITOR hotbar-refresh then sees the
    // cancelled event and correctly schedules nothing.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // (1) Direct place / hotbar number-key swap ONTO an armour slot. A head only fits the helmet cell, so any
        // armour-slot placement of a head is a helmet-equip attempt — cancel regardless of which armour cell.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack incoming = event.getClick() == ClickType.NUMBER_KEY
                    ? player.getInventory().getItem(event.getHotbarButton())
                    : event.getCursor();
            if (isHeadItem.test(incoming)) {
                event.setCancelled(true);
                return;
            }
        }
        // (2) Shift-click auto-equip: ONLY in the player's own inventory screen (a container open routes shift-click
        // to the container instead), ONLY when the clicked head is NOT the armour slot's own content (that is an
        // UNEQUIP — keep it allowed) and the helmet slot is free (else vanilla could not equip it anyway).
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getSlotType() != InventoryType.SlotType.ARMOR
                && event.getView().getType() == InventoryType.CRAFTING
                && isHeadItem.test(event.getCurrentItem())
                && isEmpty(player.getInventory().getHelmet())) {
            event.setCancelled(true);
        }
    }

    // A drag that paints a head onto the helmet cell. The player's armour slots are only reachable in their OWN
    // inventory screen (a container view renders no armour cells), where the helmet is the fixed raw slot 5 — so
    // the raw-slot check is the cross-version path ({@code InventoryView.getSlotType(int)} does not exist on 1.8.9).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!isHeadItem.test(event.getOldCursor())
                || event.getView().getType() != InventoryType.CRAFTING) {
            return;
        }
        if (event.getRawSlots().contains(HELMET_RAW_SLOT)) {
            event.setCancelled(true);
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR;
    }
}
