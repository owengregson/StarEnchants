package feature.combat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.stores.FoodWindowStore;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins the MODIFY_FOOD hunger bridge: the gain modes and the drain mode act on opposite DIRECTIONS of
 * the same event, neither touches the other's direction, absolute outranks scale-gain on a gain, and an
 * unarmed player's hunger is left alone.
 */
class FoodWindowListenerTest {

    private static final LongSupplier NOW = () -> 50L;

    private static FoodLevelChangeEvent event(UUID playerId, int before, int after) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getFoodLevel()).thenReturn(before);
        FoodLevelChangeEvent event = mock(FoodLevelChangeEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFoodLevel()).thenReturn(after);
        return event;
    }

    @Test
    void scaleGainMultipliesTheDeltaNotTheResultingLevel() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.SCALE_GAIN, 0L, 100, 2.0);

        // A near-full bar eating for +2: the gain doubles to +4, it does NOT double the 14 already there.
        FoodLevelChangeEvent event = event(player, 14, 16);
        new FoodWindowListener(store, NOW).onFoodChange(event);

        verify(event).setFoodLevel(18);
    }

    @Test
    void scaleGainClampsToTheVanillaMaximum() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.SCALE_GAIN, 0L, 100, 4.0);

        FoodLevelChangeEvent event = event(player, 16, 18);
        new FoodWindowListener(store, NOW).onFoodChange(event);

        verify(event).setFoodLevel(20);
    }

    @Test
    void cancelDrainCancelsALossAndLeavesAGainAlone() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.CANCEL_DRAIN, 0L, 100, 0.0);

        FoodLevelChangeEvent drain = event(player, 12, 11);
        new FoodWindowListener(store, NOW).onFoodChange(drain);
        verify(drain).setCancelled(true);

        // The drain window must not touch a gain — that is scale-gain's direction, and this player has none.
        FoodLevelChangeEvent gain = event(player, 12, 15);
        new FoodWindowListener(store, NOW).onFoodChange(gain);
        verify(gain, never()).setCancelled(anyBoolean());
        verify(gain, never()).setFoodLevel(anyInt());
    }

    @Test
    void scaleGainDoesNotCancelADrain() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.SCALE_GAIN, 0L, 100, 3.0);

        FoodLevelChangeEvent drain = event(player, 12, 11);
        new FoodWindowListener(store, NOW).onFoodChange(drain);

        verify(drain, never()).setCancelled(anyBoolean());
        verify(drain, never()).setFoodLevel(anyInt());
    }

    @Test
    void absoluteScalesTheResultingLevelNotTheDelta() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.ABSOLUTE, 0L, 100, 1.5);

        // 4 → 6 by 1.5: absolute lands on 9 (the whole resulting level); the delta answer would be 7.
        FoodLevelChangeEvent event = event(player, 4, 6);
        new FoodWindowListener(store, NOW).onFoodChange(event);

        verify(event).setFoodLevel(9);
    }

    @Test
    void absoluteClampsToTheVanillaMaximum() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.ABSOLUTE, 0L, 100, 1.5);

        FoodLevelChangeEvent event = event(player, 16, 18);
        new FoodWindowListener(store, NOW).onFoodChange(event);

        verify(event).setFoodLevel(20);
    }

    @Test
    void absoluteDoesNotTouchADrain() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.ABSOLUTE, 0L, 100, 1.5);

        FoodLevelChangeEvent drain = event(player, 12, 11);
        new FoodWindowListener(store, NOW).onFoodChange(drain);

        verify(drain, never()).setCancelled(anyBoolean());
        verify(drain, never()).setFoodLevel(anyInt());
    }

    @Test
    void aLapsedAbsoluteWindowLeavesTheGainUntouched() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.ABSOLUTE, 0L, 10, 1.5); // lapsed well before NOW = 50

        FoodLevelChangeEvent gain = event(player, 4, 6);
        new FoodWindowListener(store, NOW).onFoodChange(gain);

        verify(gain, never()).setFoodLevel(anyInt());
    }

    @Test
    void absoluteWinsOverScaleGainWhenBothAreArmed() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.ABSOLUTE, 0L, 100, 1.5);
        store.arm(player, FoodWindowStore.Type.SCALE_GAIN, 0L, 100, 3.0);

        // absolute → 9; the scale-gain answer would be 4 + 6 = 10, so a wrong precedence is visible.
        FoodLevelChangeEvent event = event(player, 4, 6);
        new FoodWindowListener(store, NOW).onFoodChange(event);

        verify(event).setFoodLevel(9);
        verify(event, never()).setFoodLevel(10);
    }

    @Test
    void anExpiredOrUnarmedWindowLeavesTheEventUntouched() {
        FoodWindowStore store = new FoodWindowStore();
        UUID player = UUID.randomUUID();
        store.arm(player, FoodWindowStore.Type.SCALE_GAIN, 0L, 10, 2.0); // lapsed well before NOW = 50

        FoodLevelChangeEvent gain = event(player, 5, 8);
        new FoodWindowListener(store, NOW).onFoodChange(gain);
        verify(gain, never()).setFoodLevel(anyInt());

        FoodLevelChangeEvent drain = event(UUID.randomUUID(), 5, 4);
        new FoodWindowListener(store, NOW).onFoodChange(drain);
        verify(drain, never()).setCancelled(anyBoolean());
    }
}
