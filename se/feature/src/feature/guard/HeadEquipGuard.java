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
import org.bukkit.inventory.PlayerInventory;

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

    /** {@code PlayerInventory} index bounds (vanilla-stable 1.8.9 → 26.x): hotbar is 0-8, storage is 9-35 — the two
     *  sections a shift-click moves a head between. */
    private static final int HOTBAR_SIZE = 9;
    private static final int STORAGE_END = 36;

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
        // (2) Shift-click of a head in the player's own inventory. Vanilla would auto-equip it to an empty helmet
        // FIRST, and only THEN fall through to a hotbar<->storage move — so a blanket cancel to stop the equip also
        // kills the legitimate move (the reported bug: a head could not be shift-clicked into the hotbar). Instead,
        // suppress vanilla and REDIRECT the head into its other storage/hotbar section, never the helmet.
        //
        // Vanilla only tries the helmet from a source it treats as loose inventory — storage, hotbar, or off-hand;
        // a CRAFTING-grid / RESULT shift-click already moves the head to the inventory (never the helmet) and an
        // ARMOR-slot one is an UNEQUIP, so both are left to vanilla. The redirect then runs only when the source is
        // a real storage/hotbar cell ({@link #targetRange} non-null — this also disambiguates a hotbar index 0-8
        // from a crafting-grid index of the same number, which the excluded CRAFTING type already screens out);
        // an off-hand head is still blocked from the helmet but not moved (a niche source). Gated to the player's
        // OWN inventory screen (a container open routes shift-click to the container) and a free helmet (a full one
        // means vanilla already skips the helmet and moves the head correctly, so leave it untouched).
        InventoryType.SlotType from = event.getSlotType();
        boolean vanillaMightEquip = from != InventoryType.SlotType.ARMOR
                && from != InventoryType.SlotType.CRAFTING
                && from != InventoryType.SlotType.RESULT;
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && vanillaMightEquip
                && event.getView().getType() == InventoryType.CRAFTING
                && isHeadItem.test(event.getCurrentItem())
                && isEmpty(player.getInventory().getHelmet())) {
            event.setCancelled(true); // block the vanilla helmet auto-equip …
            redirectToOtherSection(player, event.getSlot(), event.getCurrentItem()); // … then move it where it belongs
        }
    }

    // Reproduce vanilla's shift-click destination MINUS the helmet: a head in the hotbar (player-inventory index
    // 0-8) moves into storage (9-35) and one in storage moves into the hotbar — merging into compatible partial
    // stacks first, then filling empties, exactly as vanilla's own quick-move does. A head only ever fits the
    // helmet among the armour cells, so skipping the helmet is the whole of the fix; every other slot vanilla
    // would have chosen is a storage/hotbar cell, which this reproduces. {@code getItem}/{@code setItem} on the
    // player-inventory index and {@code updateInventory} are floor-stable (1.8.9 → 26.x). Called on the clicking
    // player's region thread (the event thread), editing only that player's own inventory — Folia-safe.
    @SuppressWarnings("deprecation") // updateInventory: resync the client after the manual, event-cancelled move
    private void redirectToOtherSection(Player player, int fromSlot, ItemStack head) {
        int[] target = targetRange(fromSlot);
        if (target == null) {
            return; // not a storage/hotbar cell (e.g. the off-hand): the equip is already cancelled — leave it put
        }
        int targetStart = target[0];
        int targetEnd = target[1]; // half-open [start, end)
        PlayerInventory inv = player.getInventory();
        ItemStack moving = head.clone();
        for (int i = targetStart; i < targetEnd && moving.getAmount() > 0; i++) { // merge into partial stacks
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR || !slot.isSimilar(moving)) {
                continue;
            }
            int room = slot.getMaxStackSize() - slot.getAmount();
            if (room <= 0) {
                continue;
            }
            int transfer = Math.min(room, moving.getAmount());
            slot.setAmount(slot.getAmount() + transfer);
            inv.setItem(i, slot);
            moving.setAmount(moving.getAmount() - transfer);
        }
        for (int i = targetStart; i < targetEnd && moving.getAmount() > 0; i++) { // then fill empty cells
            if (isEmpty(inv.getItem(i))) {
                inv.setItem(i, moving.clone());
                moving.setAmount(0);
            }
        }
        if (moving.getAmount() == head.getAmount()) {
            return; // the target section was full — nothing moved; the cancelled event leaves the head in place
        }
        inv.setItem(fromSlot, moving.getAmount() > 0 ? moving : null); // write back only what did not fit
        player.updateInventory();
    }

    /**
     * The other-section destination for a shift-click of a {@code PlayerInventory}-indexed source cell: a hotbar
     * cell (0-8) targets storage {@code [9,36)}, a storage cell (9-35) targets the hotbar {@code [0,9)}. Returns
     * {@code null} for any other index. Pure — the routing that makes a head shift-click land in the hotbar.
     */
    static int[] targetRange(int fromSlot) {
        if (fromSlot >= 0 && fromSlot < HOTBAR_SIZE) {
            return new int[] {HOTBAR_SIZE, STORAGE_END}; // hotbar → storage
        }
        if (fromSlot >= HOTBAR_SIZE && fromSlot < STORAGE_END) {
            return new int[] {0, HOTBAR_SIZE}; // storage → hotbar
        }
        return null;
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
