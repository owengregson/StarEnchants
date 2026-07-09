package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feature.compat.Hands;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/**
 * Pins the F23 totem-pop suppression decision (which hand vanilla would pop, and whether it is a holy scroll)
 * and that registration binds {@code EntityResurrectEvent} on a version that has it. The live event-order proof
 * (a fatal hit with a default-likeness scroll ends in death + kept marked items) is a milestone matrix check.
 */
class HolyTotemGuardTest {

    private ItemStack totem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    @Test
    void mainHandScrollIsCancelled() {
        Hands hands = mock(Hands.class);
        Player player = mock(Player.class);
        HolyScrollService service = mock(HolyScrollService.class);
        ItemStack scroll = totem();
        when(hands.mainHand(player)).thenReturn(scroll);
        when(service.isHolyScroll(scroll)).thenReturn(true);

        assertTrue(HolyTotemGuard.shouldCancel(player, hands, service));
    }

    @Test
    void mainHandRealTotemWithOffHandScrollIsNotCancelled() {
        // Vanilla pops the MAIN hand first — a real totem there wins, and we must not cancel a genuine totem.
        Hands hands = mock(Hands.class);
        Player player = mock(Player.class);
        HolyScrollService service = mock(HolyScrollService.class);
        ItemStack realTotem = totem();
        when(hands.mainHand(player)).thenReturn(realTotem);
        when(service.isHolyScroll(realTotem)).thenReturn(false);

        assertFalse(HolyTotemGuard.shouldCancel(player, hands, service));
    }

    @Test
    void offHandScrollWithEmptyMainIsCancelled() {
        Hands hands = mock(Hands.class);
        Player player = mock(Player.class);
        HolyScrollService service = mock(HolyScrollService.class);
        ItemStack scroll = totem();
        when(hands.mainHand(player)).thenReturn(null);
        when(hands.offHand(player)).thenReturn(scroll);
        when(service.isHolyScroll(scroll)).thenReturn(true);

        assertTrue(HolyTotemGuard.shouldCancel(player, hands, service));
    }

    @Test
    void noTotemInEitherHandIsNoOp() {
        Hands hands = mock(Hands.class);
        Player player = mock(Player.class);
        HolyScrollService service = mock(HolyScrollService.class);
        when(hands.mainHand(player)).thenReturn(new ItemStack(Material.DIAMOND_SWORD));
        when(hands.offHand(player)).thenReturn(null);

        assertFalse(HolyTotemGuard.shouldCancel(player, hands, service));
    }

    @Test
    void registerBindsTheResurrectEventOnAVersionThatHasIt() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pm);

        HolyTotemGuard.Path path = HolyTotemGuard.register(plugin, mock(HolyScrollService.class), mock(Hands.class));

        assertEquals(HolyTotemGuard.Path.BOUND, path);
        // ignoreCancelled=true also skips Paper's fires-even-without-a-totem pre-cancelled case.
        verify(pm).registerEvent(any(), any(), eq(EventPriority.NORMAL), any(EventExecutor.class), eq(plugin), eq(true));
    }
}
