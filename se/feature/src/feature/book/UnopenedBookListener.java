package feature.book;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click a held unopened/randomized book to reveal a random enchant book from its tier
 * (docs/v3-directives.md §I). Bukkit-thin glue — all logic is in {@link UnopenedBookService}; this only
 * recognises the gesture, cancels the vanilla interaction, consumes one unopened book, and gives the
 * rolled book. Fires on the player's own region thread (Folia-correct: reads/mutates only the player's
 * own held item), and ONLY for the main hand so a two-hand interact does not double-open.
 */
public final class UnopenedBookListener implements Listener {

    private final UnopenedBookService service;

    public UnopenedBookListener(UnopenedBookService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-open
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack used = event.getItem();
        if (used == null || !service.isUnopened(used)) {
            return; // not an unopened book — leave the interaction alone
        }
        event.setCancelled(true); // claim the gesture: the book does nothing else on right-click
        Player player = event.getPlayer();

        UnopenedResult result = service.open(used);
        if (result.opened()) {
            // Consume one unopened book from the main hand, then give the rolled book (overflow drops at feet).
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
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
