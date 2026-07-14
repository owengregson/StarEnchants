package feature.guard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/**
 * Pins the helmet-slot deny (1.8.4): every INVENTORY route a mask/pet head can reach the helmet by is cancelled,
 * while an UNEQUIP, a container shift-click and a non-head item are all left to vanilla. The predicate is
 * test-owned (identity), so this asserts the listener's gate — not any codec's membership.
 */
class HeadEquipGuardTest {

    // A real stack (not a mock) so the shift-click redirect can clone/move it; identity still drives the predicate.
    private final ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
    private final ItemStack normalItem = mock(ItemStack.class);
    private final Predicate<ItemStack> isHeadItem = stack -> stack == headItem;
    private final HeadEquipGuard guard = new HeadEquipGuard(isHeadItem);

    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);

    private InventoryClickEvent click() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        return event;
    }

    @Test
    void directPlaceOntoTheArmourSlotIsCancelled() {
        InventoryClickEvent event = click();
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getCursor()).thenReturn(headItem);

        guard.onClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void hotbarNumberKeySwapOntoTheArmourSlotIsCancelled() {
        InventoryClickEvent event = click();
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR);
        when(event.getClick()).thenReturn(ClickType.NUMBER_KEY);
        when(event.getHotbarButton()).thenReturn(3);
        when(inventory.getItem(3)).thenReturn(headItem);

        guard.onClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void shiftClickOfAHeadRedirectsToTheOtherSectionInsteadOfEquipping() {
        InventoryClickEvent event = click();
        InventoryView view = mock(InventoryView.class);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER); // shift-clicked from a storage cell
        when(event.getSlot()).thenReturn(9); // player-inventory index 9 = the first storage cell
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CRAFTING); // the player's own inventory screen
        when(event.getCurrentItem()).thenReturn(headItem);
        when(inventory.getHelmet()).thenReturn(null); // helmet free → vanilla would auto-equip
        // inventory.getItem(..) defaults to null (every hotbar cell empty), so the head lands in the first, slot 0.

        guard.onClick(event);

        verify(event).setCancelled(true);                          // the vanilla helmet auto-equip is suppressed …
        verify(inventory).setItem(eq(0), any(ItemStack.class));    // … and the head is redirected into the hotbar …
        verify(inventory).setItem(9, null);                        // … cleared from its storage source …
        verify(inventory, never()).setHelmet(any(ItemStack.class)); // … and never placed in the helmet.
    }

    @Test
    void shiftClickOfAHeadFromTheCraftingGridIsLeftToVanilla() {
        InventoryClickEvent event = click();
        InventoryView view = mock(InventoryView.class);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CRAFTING); // a grid cell moves safely to the inv …
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        when(event.getCurrentItem()).thenReturn(headItem);
        when(inventory.getHelmet()).thenReturn(null);

        guard.onClick(event);

        verify(event, never()).setCancelled(anyBoolean()); // … vanilla never equips a head to the helmet from the grid
    }

    @Test
    void targetRangeRoutesHotbarToStorageAndStorageToHotbar() {
        assertArrayEquals(new int[] {9, 36}, HeadEquipGuard.targetRange(0));  // a hotbar cell → storage
        assertArrayEquals(new int[] {9, 36}, HeadEquipGuard.targetRange(8));
        assertArrayEquals(new int[] {0, 9}, HeadEquipGuard.targetRange(9));   // a storage cell → the hotbar
        assertArrayEquals(new int[] {0, 9}, HeadEquipGuard.targetRange(35));
        assertNull(HeadEquipGuard.targetRange(-1));
        assertNull(HeadEquipGuard.targetRange(36)); // armour / offhand / out of range — no redirect
    }

    @Test
    void shiftClickUnequipFromTheArmourSlotIsLeftAlone() {
        InventoryClickEvent event = click();
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR); // the SOURCE is the armour slot — unequip
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        when(event.getCursor()).thenReturn(null); // empty cursor on a shift-click
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);

        guard.onClick(event);

        verify(event, never()).setCancelled(anyBoolean()); // a head slotted before the guard shipped can still come off
    }

    @Test
    void shiftClickIntoAnOpenContainerIsLeftAlone() {
        InventoryClickEvent event = click();
        InventoryView view = mock(InventoryView.class);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.CONTAINER);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CHEST); // a container is open — shift-click goes to it, not equip
        when(event.getCurrentItem()).thenReturn(headItem);

        guard.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void aNonHeadItemOnTheArmourSlotIsUntouched() {
        InventoryClickEvent event = click();
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getCursor()).thenReturn(normalItem);

        guard.onClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void dragThatPaintsAHeadOntoTheHelmetCellIsCancelled() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        when(event.getOldCursor()).thenReturn(headItem);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CRAFTING); // the player's own inventory screen
        when(event.getRawSlots()).thenReturn(Set.of(5)); // raw slot 5 = the helmet cell

        guard.onDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void dragOfAHeadClearOfTheHelmetCellIsLeftAlone() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        when(event.getOldCursor()).thenReturn(headItem);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CRAFTING);
        when(event.getRawSlots()).thenReturn(Set.of(20)); // a plain inventory slot

        guard.onDrag(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void dragOntoRawFiveInAContainerViewIsLeftAlone() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        when(event.getOldCursor()).thenReturn(headItem);
        when(event.getView()).thenReturn(view);
        when(view.getType()).thenReturn(InventoryType.CHEST); // raw 5 here is a chest slot, not the helmet cell

        guard.onDrag(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void dragOfANonHeadItemIsUntouched() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getOldCursor()).thenReturn(normalItem);

        guard.onDrag(event);

        verify(event, never()).setCancelled(anyBoolean());
    }
}
