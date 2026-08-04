package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.run.ActivationContext;
import feature.compat.Hands;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A click's own block is the only source {@code %block.type%}/{@code %isblock%} have on the INTERACT family,
 * and {@code @Here} anchors on the same context. Dropping it fails in the two worst ways at once: a gate on
 * the clicked material never matches (the ability is silently dead), and a block effect that skips the gate
 * lands at the CLICKER'S FEET instead of the face they hit — a block-eraser aimed at the wrong block.
 */
class InteractBlockContextTest {

    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        return player;
    }

    private static Block block() {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(mock(Location.class));
        return block;
    }

    /** Every context the click fired, in order: the bare INTERACT first, then its directional twin. */
    private List<ActivationContext> fired(Player player, Action action, Block clicked) {
        Hands hands = mock(Hands.class);
        when(hands.isMainHand(any())).thenReturn(true);
        new TriggerListeners(dispatch, hands)
                .onInteract(new PlayerInteractEvent(player, action, null, clicked, BlockFace.UP));
        ArgumentCaptor<ActivationContext> context = ArgumentCaptor.forClass(ActivationContext.class);
        verify(dispatch, times(2)).fire(eq(player), anyInt(), context.capture(), any());
        return context.getAllValues();
    }

    @Test
    void aClickOnABlockCarriesThatBlockAndAnchorsThere() {
        Player player = player();
        Block clicked = block();

        ActivationContext context = fired(player, Action.LEFT_CLICK_BLOCK, clicked).get(0);

        assertSame(clicked, context.block());
        // Not the player's own location: @Here reads this, and the clicked face is what the click meant.
        assertSame(clicked.getLocation(), context.location());
    }

    @Test
    void aRightClickCarriesItsBlockToo() {
        // Both directions feed the same facts — a use-item gating on the block it was clicked against reads
        // the RIGHT_CLICK path, and only the left one having a block would strand it.
        Player player = player();
        Block clicked = block();

        assertSame(clicked, fired(player, Action.RIGHT_CLICK_BLOCK, clicked).get(0).block());
    }

    @Test
    void aClickAtOpenAirStaysTheBareSelfContext() {
        // No block to carry, so nothing changes: %isblock% stays false and @Here stays the clicker. A swing at
        // air must not inherit whatever block the previous click touched.
        Player player = player();

        ActivationContext context = fired(player, Action.LEFT_CLICK_AIR, null).get(0);

        assertNull(context.block());
        assertSame(player.getLocation(), context.location());
    }

    @Test
    void theDirectionalFireSeesTheSameFactsAsTheBareOne() {
        // One click is one activation. If INTERACT and INTERACT_LEFT were handed different contexts, the same
        // condition would read differently depending only on which trigger the author picked.
        Player player = player();
        Block clicked = block();

        List<ActivationContext> contexts = fired(player, Action.LEFT_CLICK_BLOCK, clicked);

        assertEquals(2, contexts.size());
        assertSame(contexts.get(0), contexts.get(1));
    }
}
