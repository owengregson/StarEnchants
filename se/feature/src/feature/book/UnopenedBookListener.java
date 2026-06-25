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
 * Right-click a held unopened/randomized book to reveal a random enchant book from its tier (§I).
 * Bukkit-thin glue — all logic is in {@link UnopenedBookService}. Folia-correct: fires on the player's
 * own region thread, touching only their own held item. Main-hand only, so a two-hand interact does not
 * double-open.
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
            return;
        }
        event.setCancelled(true); // claim the gesture: the book does nothing else on right-click
        Player player = event.getPlayer();

        UnopenedResult result = service.open(used);
        if (result.opened()) {
            // Consume one from the main hand; rolled-book overflow drops at the player's feet.
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
