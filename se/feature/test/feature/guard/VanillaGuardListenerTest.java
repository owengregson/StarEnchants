package feature.guard;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feature.compat.Hands;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Pins the placement backstop: a plugin item is denied outright at {@link BlockPlaceEvent} (no place-then-refund)
 * AND the client's predicted held-count is resynced so the cancel never half-consumes / desyncs (1.8.4); a
 * non-plugin item is left entirely to vanilla. The predicate is test-owned (identity), so this asserts the
 * listener's gate — cancel iff {@code isPluginItem} — not any codec's membership.
 */
class VanillaGuardListenerTest {

    private final ItemStack pluginItem = mock(ItemStack.class);
    private final ItemStack normalItem = mock(ItemStack.class);
    private final Predicate<ItemStack> isPluginItem = stack -> stack == pluginItem;
    private final VanillaGuardListener guard = new VanillaGuardListener(isPluginItem, mock(Hands.class));

    @Test
    void pluginItemPlacementIsCancelledAndResynced() {
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        Player player = mock(Player.class);
        when(place.getItemInHand()).thenReturn(pluginItem);
        when(place.getPlayer()).thenReturn(player);

        guard.onPlace(place);

        verify(place).setCancelled(true);
        verify(player).updateInventory(); // the held-slot resync — no place-then-refund flicker
    }

    @Test
    void normalItemPlacementIsUntouched() {
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getItemInHand()).thenReturn(normalItem);

        guard.onPlace(place);

        verify(place, never()).setCancelled(anyBoolean()); // must not break ordinary block placement
    }
}
