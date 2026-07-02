package feature.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.Lang;
import java.util.HashMap;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import platform.lang.Messages;

/**
 * Truth-table for the shared apply gesture (ADR-0041): the guard preamble falls through, and each commit-flag
 * combination writes exactly the cursor/target/give/update/message the family variants rely on. ItemStack
 * arguments are matched by identity ({@code same}), never {@code equals}, so no Bukkit server is needed.
 */
@SuppressWarnings("deprecation") // setCursor: the base uses the floor-stable cursor path, so the test verifies it
class ApplyGestureListenerTest {

    private static final Material CURSOR = Material.DIAMOND;
    private static final Material TARGET = Material.IRON_SWORD;

    /** A leaf that claims a {@link #CURSOR}-typed cursor and returns a scripted outcome (its apply may mutate). */
    private static final class FakeLeaf extends ApplyGestureListener {
        private final BiFunction<ItemStack, ItemStack, GestureOutcome> applyFn;
        int applyCalls = 0;

        FakeLeaf(Messages messages, BiFunction<ItemStack, ItemStack, GestureOutcome> applyFn) {
            super(messages);
            this.applyFn = applyFn;
        }

        @Override
        protected boolean claimsCursor(ItemStack cursor) {
            return cursor.getType() == CURSOR;
        }

        @Override
        protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
            applyCalls++;
            return applyFn.apply(cursor, target);
        }
    }

    private Player player;
    private PlayerInventory playerInv;
    private World world;
    private Location location;
    private Inventory bottom;
    private Inventory top;

    private Messages messages() {
        return new Messages(Lang::defaults);
    }

    /** Build a click; {@code topClick} routes {@code getClickedInventory} to the top (else the bottom) inventory. */
    private InventoryClickEvent event(ClickType click, ItemStack cursor, ItemStack current, boolean topClick) {
        player = mock(Player.class);
        playerInv = mock(PlayerInventory.class);
        world = mock(World.class);
        location = mock(Location.class);
        bottom = mock(Inventory.class);
        top = mock(Inventory.class);
        when(player.getInventory()).thenReturn(playerInv);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);
        when(location.getWorld()).thenReturn(world);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(click);
        when(event.getCursor()).thenReturn(cursor);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getSlot()).thenReturn(0);
        when(event.getView()).thenReturn(view);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getTopInventory()).thenReturn(top);
        when(event.getClickedInventory()).thenReturn(topClick ? top : bottom);
        return event;
    }

    private ItemStack cursor(int amount) {
        return new ItemStack(CURSOR, amount);
    }

    private ItemStack target() {
        return new ItemStack(TARGET, 1);
    }

    @Test
    void guardsFallThrough() {
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> GestureOutcome.noop("m"));

        // SHIFT_LEFT is not a claimed click shape.
        InventoryClickEvent shift = event(ClickType.SHIFT_LEFT, cursor(1), target(), false);
        leaf.onGestureClick(shift);
        verify(shift, never()).setCancelled(anyBoolean());

        // A click in the TOP inventory.
        InventoryClickEvent topClick = event(ClickType.LEFT, cursor(1), target(), true);
        leaf.onGestureClick(topClick);
        verify(topClick, never()).setCancelled(anyBoolean());

        // Cursor not of this family.
        InventoryClickEvent unclaimed = event(ClickType.LEFT, new ItemStack(Material.STONE), target(), false);
        leaf.onGestureClick(unclaimed);
        verify(unclaimed, never()).setCancelled(anyBoolean());

        // Null / AIR target.
        InventoryClickEvent nullTarget = event(ClickType.LEFT, cursor(1), null, false);
        leaf.onGestureClick(nullTarget);
        verify(nullTarget, never()).setCancelled(anyBoolean());
        InventoryClickEvent airTarget = event(ClickType.LEFT, cursor(1), new ItemStack(Material.AIR), false);
        leaf.onGestureClick(airTarget);
        verify(airTarget, never()).setCancelled(anyBoolean());

        // claimsTarget=false: the default rejects X-onto-X (the target is itself a cursor item of this family).
        InventoryClickEvent xOnX = event(ClickType.LEFT, cursor(1), cursor(1), false);
        leaf.onGestureClick(xOnX);
        verify(xOnX, never()).setCancelled(anyBoolean());

        assertEquals(0, leaf.applyCalls, "apply must never run when a guard rejects the click");
    }

    @Test
    void noopCancelsAndMessagesOnly() {
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> GestureOutcome.noop("&cnope"));
        InventoryClickEvent event = event(ClickType.LEFT, cursor(1), target(), false);
        leaf.onGestureClick(event);
        verify(event).setCancelled(true);
        verify(event, never()).setCursor(any());
        verify(event, never()).setCurrentItem(any());
        verify(player, never()).updateInventory();
        verify(player).sendMessage("&cnope");
    }

    @Test
    void committedWritesCursorTargetAndUpdates() {
        ItemStack cursor = cursor(2);
        ItemStack newTarget = new ItemStack(TARGET, 1);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> {
            c.setAmount(c.getAmount() - 1); // spend one
            return GestureOutcome.committed(newTarget, "m");
        });
        InventoryClickEvent event = event(ClickType.LEFT, cursor, target(), false);
        leaf.onGestureClick(event);
        verify(event).setCursor(same(cursor)); // amount 1 > 0 → the decremented stack is written back
        verify(event).setCurrentItem(same(newTarget));
        verify(player).updateInventory();
        verify(player).sendMessage("m");
    }

    @Test
    void committedZeroCursorClears() {
        ItemStack cursor = cursor(1);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> {
            c.setAmount(0);
            return GestureOutcome.committed(new ItemStack(TARGET), "m");
        });
        InventoryClickEvent event = event(ClickType.LEFT, cursor, target(), false);
        leaf.onGestureClick(event);
        verify(event).setCursor(null); // amount 0 → the cursor slot is cleared
        verify(player).updateInventory();
    }

    @Test
    void committedNullTargetClearsSlot() {
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> GestureOutcome.committed(null, "m"));
        InventoryClickEvent event = event(ClickType.LEFT, cursor(1), target(), false);
        leaf.onGestureClick(event);
        verify(event).setCurrentItem(null); // heroic destroy-on-fail
    }

    @Test
    void committedZeroAmountTargetClearsSlot() {
        ItemStack shattered = new ItemStack(TARGET, 1);
        shattered.setAmount(0);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> GestureOutcome.committed(shattered, "m"));
        InventoryClickEvent event = event(ClickType.LEFT, cursor(1), target(), false);
        leaf.onGestureClick(event);
        verify(event).setCurrentItem(null); // carrier shatter (amount 0)
    }

    @Test
    void consumedOnlySkipsTargetWrite() {
        ItemStack cursor = cursor(2);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> {
            c.setAmount(c.getAmount() - 1);
            return GestureOutcome.consumedOnly("m");
        });
        InventoryClickEvent event = event(ClickType.LEFT, cursor, target(), false);
        leaf.onGestureClick(event);
        verify(event).setCursor(same(cursor));
        verify(event, never()).setCurrentItem(any());
        verify(player).updateInventory();
    }

    @Test
    void producedIsGivenWithOverflowBeforeUpdate() {
        ItemStack produced = new ItemStack(Material.PAPER);
        ItemStack leftover = new ItemStack(Material.PAPER);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> GestureOutcome.committed(new ItemStack(TARGET), produced, "m"));
        InventoryClickEvent event = event(ClickType.LEFT, cursor(1), target(), false);
        HashMap<Integer, ItemStack> overflow = new HashMap<>();
        overflow.put(0, leftover);
        when(playerInv.addItem(any(ItemStack.class))).thenReturn(overflow);
        leaf.onGestureClick(event);
        verify(world).dropItemNaturally(same(location), same(leftover));
        InOrder order = inOrder(playerInv, player);
        order.verify(playerInv).addItem(same(produced)); // give before…
        order.verify(player).updateInventory();           // …the inventory refresh
    }

    @Test
    void followUpRunsLast() {
        Runnable followUp = mock(Runnable.class);
        ItemStack cursor = cursor(2);
        FakeLeaf leaf = new FakeLeaf(messages(), (c, t) -> {
            c.setAmount(c.getAmount() - 1);
            return GestureOutcome.consumedOnly(null).andThen(followUp);
        });
        InventoryClickEvent event = event(ClickType.LEFT, cursor, target(), false);
        leaf.onGestureClick(event);
        InOrder order = inOrder(event, player, followUp);
        order.verify(event).setCursor(same(cursor));
        order.verify(player).updateInventory();
        order.verify(followUp).run();
        verify(player, never()).sendMessage(any(String.class)); // message null → no send
    }

    @Test
    void feedbackGateSilencesGestureMessages() {
        Messages gated = new Messages(Lang::defaults, () -> "", () -> false);
        FakeLeaf leaf = new FakeLeaf(gated, (c, t) -> GestureOutcome.committed(new ItemStack(TARGET), "m"));
        InventoryClickEvent event = event(ClickType.LEFT, cursor(1), target(), false);
        leaf.onGestureClick(event);
        verify(player, never()).sendMessage(any(String.class));
    }
}
