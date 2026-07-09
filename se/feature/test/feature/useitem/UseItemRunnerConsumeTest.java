package feature.useitem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.UseItemDef;
import feature.compat.Hands;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * G08: {@link UseItemRunner} charges one item on a successful consumable use by the key-stamped fallback chain
 * (main hand → off-hand → inventory scan) instead of the old fail-open main-hand-only guard. The off-hand arm
 * charges the is-food eat route (the food is eaten from the off-hand); the scan arm charges an effect that swapped
 * or refilled the hand; only a stack carrying THIS use-item's key is ever decremented, and an emptied hand never
 * NPEs. Pure logic over the {@link Hands} seam and inventory indices (the live happy path is the matrix suite).
 */
class UseItemRunnerConsumeTest {

    private static final String KEY = "use:rage";
    private static final UseItemDef DEF = new UseItemDef("rage", "&cRage", "RED_DYE", List.of(),
            true, false, false, "", 0, List.of(""), List.of(KEY));

    private UseItemService service;
    private Hands hands;
    private UseItemRunner runner;
    private Player player;

    @BeforeEach
    void setUp() {
        service = mock(UseItemService.class);
        hands = mock(Hands.class);
        runner = new UseItemRunner(service, mock(Messages.class), hands);
        player = mock(Player.class);
        when(service.defOf(KEY)).thenReturn(DEF);
        when(service.use(any(), any())).thenReturn(UseOutcome.activated()); // drive the ACTIVATED consume tail
    }

    @Test
    void mainHandStillMatchesSoTheHandIsChargedAndInventoryUntouched() {
        ItemStack main = mock(ItemStack.class);
        when(main.getAmount()).thenReturn(3);
        when(service.keyOf(main)).thenReturn(KEY);
        when(hands.mainHand(player)).thenReturn(main);

        runner.activate(player, KEY);

        verify(main).setAmount(2);
        verify(hands).setMainHand(player, main);
        verify(hands, never()).offHand(any()); // main matched → never even reads the off-hand
        verify(hands, never()).setOffHand(any(), any());
    }

    @Test
    void mainHandMismatchButOffHandMatchesChargesTheOffHand() {
        ItemStack main = mock(ItemStack.class);
        when(service.keyOf(main)).thenReturn("use:other");
        when(hands.mainHand(player)).thenReturn(main);
        ItemStack off = mock(ItemStack.class);
        when(off.getAmount()).thenReturn(2);
        when(service.keyOf(off)).thenReturn(KEY);
        when(hands.offHand(player)).thenReturn(off);

        runner.activate(player, KEY);

        verify(off).setAmount(1);
        verify(hands).setOffHand(player, off);
        verify(hands, never()).setMainHand(any(), any());
    }

    @Test
    void neitherHandButAnInventoryStackMatchesChargesThatSlotAndClearsWhenEmptied() {
        ItemStack main = mock(ItemStack.class);
        when(service.keyOf(main)).thenReturn("use:other");
        when(hands.mainHand(player)).thenReturn(main);
        ItemStack off = mock(ItemStack.class);
        when(service.keyOf(off)).thenReturn("use:other");
        when(hands.offHand(player)).thenReturn(off);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getSize()).thenReturn(27);
        ItemStack stashed = mock(ItemStack.class);
        when(stashed.getAmount()).thenReturn(1, 0); // 1 when decrementing, 0 on the empties-check re-read
        when(service.keyOf(stashed)).thenReturn(KEY);
        when(inv.getItem(12)).thenReturn(stashed);

        runner.activate(player, KEY);

        verify(stashed).setAmount(0);
        verify(inv).setItem(12, null); // decremented to empty → the slot is cleared, not left with a 0-count stack
    }

    @Test
    void nothingCarriesTheKeySoNothingIsChargedAndNoException() {
        ItemStack main = mock(ItemStack.class);
        when(service.keyOf(main)).thenReturn("use:other");
        when(hands.mainHand(player)).thenReturn(main);
        when(hands.offHand(player)).thenReturn(null);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getSize()).thenReturn(27); // every slot empty (the effect destroyed the item — not a free ride)

        runner.activate(player, KEY);

        verify(hands, never()).setMainHand(any(), any());
        verify(hands, never()).setOffHand(any(), any());
        verify(inv, never()).setItem(anyInt(), any());
    }

    @Test
    void legacyShapeOffHandNullFallsThroughToTheScanWithoutNpe() {
        ItemStack main = mock(ItemStack.class);
        when(service.keyOf(main)).thenReturn("use:other");
        when(hands.mainHand(player)).thenReturn(main);
        when(hands.offHand(player)).thenReturn(null); // 1.8.9: no off-hand
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getSize()).thenReturn(36);
        ItemStack stashed = mock(ItemStack.class);
        when(stashed.getAmount()).thenReturn(2);
        when(service.keyOf(stashed)).thenReturn(KEY);
        when(inv.getItem(5)).thenReturn(stashed);

        runner.activate(player, KEY);

        verify(stashed).setAmount(1);
        verify(inv).setItem(5, stashed); // still charged; null off-hand did not break the chain
    }
}
