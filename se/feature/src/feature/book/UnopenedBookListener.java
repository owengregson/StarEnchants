package feature.book;

import feature.apply.GestureOutcome;
import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.lang.Messages;

/**
 * Right-click a held unopened/randomized book to reveal a random enchant book from its tier (§I).
 * Bukkit-thin glue — logic is in {@link UnopenedBookService}. This ADOPTS the {@link GestureOutcome} shape and
 * {@link Inventories}/{@link Messages#sendText} seams (ADR-0041) but is NOT an {@code ApplyGestureListener}: it
 * is a {@code PlayerInteractEvent} (a held right-click), not a cursor-onto-gear click. Folia-correct: fires on
 * the player's own region thread, touching only their own held item.
 */
public final class UnopenedBookListener implements Listener {

    private final UnopenedBookService service;
    private final Messages messages;
    private final Hands hands;

    public UnopenedBookListener(UnopenedBookService service, Messages messages, Hands hands) {
        this.service = Objects.requireNonNull(service, "service");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    // priority LOW, NOT ignoreCancelled: a RIGHT_CLICK_BLOCK with a non-use item often arrives already
    // cancelled (vanilla deny / a protection plugin), which would silently drop the first open. LOW still
    // precedes TriggerListeners (HIGH), so the book claims the gesture ahead of INTERACT triggers.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-open
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Read from the main hand directly (not event.getItem(), which can be null on an air-click).
        ItemStack used = hands.mainHand(player);
        if (used == null || !service.isUnopened(used)) {
            return;
        }
        event.setCancelled(true); // claim the gesture: the book does nothing else on right-click

        GestureOutcome result = service.open(used);
        if (result.commit()) {
            ItemStack hand = hands.mainHand(player);
            hand.setAmount(hand.getAmount() - 1);
            hands.setMainHand(player, hand.getAmount() <= 0 ? null : hand);
        }
        if (result.produced() != null) {
            Inventories.giveOrDrop(player, result.produced());
        }
        if (result.message() != null) {
            messages.sendText(player, result.message());
        }
    }
}
