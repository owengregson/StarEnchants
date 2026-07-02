package platform.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Pins the give-with-overflow contract (ADR-0041): fitting items never drop; overflow drops at the right place. */
class InventoriesTest {

    private final ItemStack item = new ItemStack(Material.PAPER);
    private final ItemStack extra = new ItemStack(Material.PAPER);

    @Test
    void allFitsNoDrop() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location feet = mock(Location.class);
        when(player.getInventory()).thenReturn(inv);
        when(player.getLocation()).thenReturn(feet);
        when(feet.getWorld()).thenReturn(world);
        when(inv.addItem(item)).thenReturn(new HashMap<>());

        Inventories.giveOrDrop(player, item);
        verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void overflowDropsAtFeet() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location feet = mock(Location.class);
        when(player.getInventory()).thenReturn(inv);
        when(player.getLocation()).thenReturn(feet);
        when(feet.getWorld()).thenReturn(world);
        HashMap<Integer, ItemStack> overflow = new HashMap<>();
        overflow.put(0, extra);
        when(inv.addItem(item)).thenReturn(overflow);

        Inventories.giveOrDrop(player, item);
        verify(world).dropItemNaturally(feet, extra);
    }

    @Test
    void explicitLocationOverloadDropsThere() {
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location blockLoc = mock(Location.class);
        when(player.getInventory()).thenReturn(inv);
        when(blockLoc.getWorld()).thenReturn(world);
        HashMap<Integer, ItemStack> overflow = new HashMap<>();
        overflow.put(0, extra);
        when(inv.addItem(item)).thenReturn(overflow);

        Inventories.giveOrDrop(player, item, blockLoc);
        verify(world).dropItemNaturally(blockLoc, extra); // pins the MineDrops block-drop behaviour
    }
}
