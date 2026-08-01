package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feature.compat.Sounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * Pins the F12 two-phase death handling: holy items are pulled from the drops at HIGH but only committed to the
 * stash at MONITOR when keepInventory is still false. A later handler flipping keepInventory on (a grave / keep
 * plugin at HIGHEST) means vanilla kept the live inventory — including the originals — so the parked copies are
 * discarded instead of duplicating them one tick after respawn.
 */
class HolyScrollListenerTest {

    private final UUID id = UUID.randomUUID();

    private PlayerDeathEvent deathEvent(Player player, boolean... keepReads) {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(new ArrayList<>());
        Boolean first = keepReads[0];
        Boolean[] rest = new Boolean[keepReads.length - 1];
        for (int i = 1; i < keepReads.length; i++) {
            rest[i - 1] = keepReads[i];
        }
        when(event.getKeepInventory()).thenReturn(first, rest);
        return event;
    }

    private HolyScrollListener listener(HolyScrollService service, KeptItemsStore kept, Messages messages) {
        return new HolyScrollListener(service, kept, messages, mock(Sounds.class));
    }

    @Test
    void keepFalseThroughoutStashesAndMessages() {
        HolyScrollService service = mock(HolyScrollService.class);
        KeptItemsStore kept = new KeptItemsStore();
        Messages messages = mock(Messages.class);
        List<ItemStack> pulled = new ArrayList<>(List.of(mock(ItemStack.class)));
        when(service.keepFromDrops(any(), any())).thenReturn(pulled);
        when(service.keptMessage(1)).thenReturn("kept-1");

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        PlayerDeathEvent event = deathEvent(player, false, false); // HIGH false, MONITOR false

        HolyScrollListener listener = listener(service, kept, messages);
        listener.onDeath(event);
        listener.onDeathCommit(event);

        assertEquals(1, kept.drain(id).size(), "the pulled holy item is committed to the stash");
        verify(messages).sendText(player, "kept-1");
    }

    @Test
    void keepFlippedTrueAfterHighDiscardsWithoutStashOrMessage() {
        HolyScrollService service = mock(HolyScrollService.class);
        KeptItemsStore kept = new KeptItemsStore();
        Messages messages = mock(Messages.class);
        when(service.keepFromDrops(any(), any())).thenReturn(new ArrayList<>(List.of(mock(ItemStack.class))));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        PlayerDeathEvent event = deathEvent(player, false, true); // HIGH false, MONITOR flipped true

        HolyScrollListener listener = listener(service, kept, messages);
        listener.onDeath(event);
        listener.onDeathCommit(event);

        assertTrue(kept.drain(id).isEmpty(), "the un-cleared live inventory already holds the originals — no stash");
        verify(messages, never()).sendText(any(), anyString());
    }

    @Test
    void keepTrueAtHighParksNothing() {
        HolyScrollService service = mock(HolyScrollService.class);
        KeptItemsStore kept = new KeptItemsStore();
        Messages messages = mock(Messages.class);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        PlayerDeathEvent event = deathEvent(player, true, true); // gamerule/enchant already keeps everything

        HolyScrollListener listener = listener(service, kept, messages);
        listener.onDeath(event);
        listener.onDeathCommit(event);

        verify(service, never()).keepFromDrops(any(), any());
        assertTrue(kept.drain(id).isEmpty());
        verify(messages, never()).sendText(any(), anyString());
        verify(service, never()).keptMessage(anyInt());
    }
}
