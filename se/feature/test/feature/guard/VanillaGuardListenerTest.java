package feature.guard;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feature.compat.Hands;
import java.util.function.Predicate;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Pins the placement backstop: a plugin item is denied outright at {@link BlockPlaceEvent} (no place-then-refund),
 * a non-plugin item is left to vanilla. The predicate is test-owned (identity), so this asserts the listener's
 * gate — cancel iff {@code isPluginItem} — not any codec's membership.
 */
class VanillaGuardListenerTest {

    private final ItemStack pluginItem = mock(ItemStack.class);
    private final ItemStack normalItem = mock(ItemStack.class);
    private final Predicate<ItemStack> isPluginItem = stack -> stack == pluginItem;
    private final VanillaGuardListener guard = new VanillaGuardListener(isPluginItem, mock(Hands.class));

    @Test
    void pluginItemPlacementIsCancelled() {
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getItemInHand()).thenReturn(pluginItem);

        guard.onPlace(place);

        verify(place).setCancelled(true);
    }

    @Test
    void normalItemPlacementIsUntouched() {
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getItemInHand()).thenReturn(normalItem);

        guard.onPlace(place);

        verify(place, never()).setCancelled(anyBoolean()); // must not break ordinary block placement
    }
}
