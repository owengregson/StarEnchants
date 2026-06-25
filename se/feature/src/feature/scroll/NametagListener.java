package feature.scroll;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * Item-nametag gesture + chat-capture glue (§I); logic lives in {@link NametagService}. Folia footgun:
 * {@code AsyncPlayerChatEvent} fires async, so the inventory mutation is hopped back to the player's region thread.
 */
public final class NametagListener implements Listener {

    private final NametagService service;

    public NametagListener(NametagService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!service.isNametag(cursor)) {
            return; // the cursor is not a nametag — leave the click alone
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isNametag(target)) {
            return; // no target, or nametag-onto-nametag (meaningless)
        }

        event.setCancelled(true);
        // begin() refuses (null) when a rename is already pending, so a second nametag isn't consumed for nothing.
        String prompt = service.begin(player.getUniqueId(), target);
        if (prompt == null) {
            player.sendMessage(service.busyMessage()); // §L lang.yml scroll.nametag.busy
            return; // do NOT consume a nametag while a rename is already awaiting chat
        }
        cursor.setAmount(cursor.getAmount() - 1); // a nametag is spent to begin the rename (refunded if aborted)
        event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
        player.updateInventory();
        player.sendMessage(prompt);
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent: the floor-stable chat-capture path (1.17.1 → 26.1.x)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!service.isPending(player.getUniqueId())) {
            return; // no rename awaiting this player's chat
        }
        event.setCancelled(true); // claim the line: it names the item, it is not broadcast
        String text = event.getMessage();
        // The mutation touches the player's inventory; chat is async, so hop to their region thread (Folia).
        Scheduling.onEntity(player, () -> {
            String message = service.complete(player, text);
            if (message != null) {
                player.sendMessage(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clear(event.getPlayer().getUniqueId()); // never reuse a stale slot after a relog
    }
}
