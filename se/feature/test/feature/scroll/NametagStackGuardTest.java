package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ScrollsConfig;
import item.codec.ScrollCodec;
import item.mint.ItemFactory;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;

/**
 * F15: {@link NametagService#complete} re-checks stack size at commit and refuses a grown stack — the residual
 * hole {@code locate()} leaves open because {@link ItemStack#isSimilar} ignores amount, so a single captured item
 * can be grown into a stack (the 1.8.9 chat-capture window) before the name is typed. The refuse path refunds the
 * nametag exactly like target-gone, and a single item still renames. The listener-side pre-guard is a one-line
 * clone of the shipped {@code common.single-item} pattern and is exercised by the sibling apply-family tests.
 *
 * <p>Server-free: the refund's {@code mint()} is routed through {@link ItemFactory#customItemResolver} so no live
 * ItemFactory is needed; every ItemStack/inventory read is a Mockito stub. {@code combat} is the documented null
 * (suffix-skipping) wiring.
 */
class NametagStackGuardTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private NametagService service;
    private Messages messages;
    private Player player;
    private PlayerInventory inv;
    private ItemStack nametag; // the minted refund token the resolver hands back

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        when(messages.format("common.single-item")).thenReturn("SINGLE");
        service = new NametagService(mock(ScrollCodec.class), ScrollsConfig::defaults, messages, null);

        nametag = mock(ItemStack.class);
        when(nametag.clone()).thenReturn(nametag); // mint decorates the clone; null meta → decorate is a no-op
        ItemFactory.customItemResolver(token -> nametag); // mint() short-circuits before any live Material lookup

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getSize()).thenReturn(1);
        when(inv.addItem(any())).thenReturn(new HashMap<>()); // refund overflow: none
    }

    @AfterEach
    void tearDown() {
        ItemFactory.customItemResolver(null); // restore the inert default so other tests aren't perturbed
    }

    @Test
    void aGrownStackIsRefusedAndTheNametagRefunded() {
        ItemStack target = mock(ItemStack.class);
        ItemStack token = mock(ItemStack.class);
        when(target.clone()).thenReturn(token);
        service.begin(PLAYER, target);

        ItemStack grown = mock(ItemStack.class); // the captured single item grew to 2 before confirm
        when(grown.getAmount()).thenReturn(2);
        when(grown.isSimilar(token)).thenReturn(true);
        when(inv.getItem(0)).thenReturn(grown);

        assertEquals("SINGLE", service.complete(player, "MyName"));
        verify(grown, never()).setItemMeta(any()); // the stack was NOT renamed
        verify(inv).addItem(nametag);               // the nametag was refunded
    }

    @Test
    void aSingleItemStillRenames() {
        ItemStack target = mock(ItemStack.class);
        ItemStack token = mock(ItemStack.class);
        when(target.clone()).thenReturn(token);
        service.begin(PLAYER, target);

        ItemStack single = mock(ItemStack.class);
        when(single.getAmount()).thenReturn(1);
        when(single.isSimilar(token)).thenReturn(true);
        when(single.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(inv.getItem(0)).thenReturn(single);

        service.complete(player, "MyName");
        verify(single).setItemMeta(any()); // rename committed
        verify(inv, never()).addItem(any()); // no refund — the item was consumed by the rename
    }
}
