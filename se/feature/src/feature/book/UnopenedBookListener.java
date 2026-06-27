package feature.book;

import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click a held unopened/randomized book to reveal a random enchant book from its tier (§I).
 * Bukkit-thin glue — logic is in {@link UnopenedBookService}. Folia-correct: fires on the player's own
 * region thread, touching only their own held item.
 */
public final class UnopenedBookListener implements Listener {

    private final UnopenedBookService service;

    public UnopenedBookListener(UnopenedBookService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    // priority LOW, NOT ignoreCancelled: a RIGHT_CLICK_BLOCK with a non-use item often arrives already
    // cancelled (vanilla deny / a protection plugin), which would silently drop the first open. LOW still
    // precedes TriggerListeners (HIGH), so the book claims the gesture ahead of INTERACT triggers.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!Hands.isMainHand(event)) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-open
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Read from the main hand directly (not event.getItem(), which can be null on an air-click).
        ItemStack used = Hands.mainHand(player);
        if (used == null || !service.isUnopened(used)) {
            return;
        }
        event.setCancelled(true); // claim the gesture: the book does nothing else on right-click

        UnopenedResult result = service.open(used);
        if (result.opened()) {
            ItemStack hand = Hands.mainHand(player);
            hand.setAmount(hand.getAmount() - 1);
            Hands.setMainHand(player, hand.getAmount() <= 0 ? null : hand);
            if (result.produced() != null) {
                player.getInventory().addItem(result.produced()).values()
                        .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            }
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
