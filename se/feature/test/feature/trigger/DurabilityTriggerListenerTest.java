package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.run.ActivationContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code %item.durabilitypercent%} is read HERE, off the exact stack the event names — the only place the
 * damaged item is in hand. A wrong read fails silently: the fact would price some other item's wear, or none.
 */
class DurabilityTriggerListenerTest {

    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private static ItemStack stack(Material type, ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    private ActivationContext fired(Player player, ItemStack item) {
        new DurabilityTriggerListener(dispatch).onItemDamage(new PlayerItemDamageEvent(player, item, 1));
        ArgumentCaptor<ActivationContext> context = ArgumentCaptor.forClass(ActivationContext.class);
        verify(dispatch).fire(eq(player), anyInt(), context.capture(), any());
        return context.getValue();
    }

    @Test
    void theFiredContextPricesTheDamagedStacksRemainingDurability() {
        int max = Material.DIAMOND_SWORD.getMaxDurability();
        Damageable meta = mock(Damageable.class);
        when(meta.getDamage()).thenReturn(max - 1); // one point from breaking
        Player player = player();

        // Measured BEFORE this wear lands (the event has not applied it yet) — %damage% carries the points
        // about to be lost, so an author can price either side without the engine choosing for them.
        assertEquals(DurabilityPercent.of(max - 1, max), fired(player, stack(Material.DIAMOND_SWORD, meta)).itemDurabilityPercent());
    }

    @Test
    void anItemWithNoDurabilityBarLeavesTheFactAbsent() {
        // Absent, not 0: a reading of 0 says "spent", and a condition gating a repair would fire on it.
        assertTrue(Double.isNaN(
                fired(player(), stack(Material.STONE, mock(ItemMeta.class))).itemDurabilityPercent()));
    }
}
