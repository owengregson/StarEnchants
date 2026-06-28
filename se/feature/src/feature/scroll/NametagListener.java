package feature.scroll;

import feature.compat.MenuClicks;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * Item-nametag gesture glue (§I); logic lives in {@link NametagService}. Dragging a nametag onto gear begins a
 * rename. On modern servers ({@link NametagAnvil#supported()}) the rename is captured through a raw-Bukkit
 * ANVIL GUI — the player types the new name, legacy {@code &} colour codes are parsed, and the result-slot
 * click applies it; the 1.8.9 fork falls back to chat capture.
 *
 * <p>Folia-correct: inventory events fire on the clicking player's own region thread; {@code AsyncPlayerChatEvent}
 * is async, so its mutation is hopped back.
 */
public final class NametagListener implements Listener {

    private final NametagService service;
    private final boolean anvilMode = NametagAnvil.supported();

    public NametagListener(NametagService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 1) A click inside OUR anvil rename GUI: lock the dialog; the result slot confirms the rename.
        if (anvilMode && service.inAnvil(player.getUniqueId()) && NametagAnvil.isAnvil(event.getView())) {
            event.setCancelled(true); // typing renames; no item may be pulled out
            if (event.getRawSlot() == NametagAnvil.RESULT_SLOT) {
                confirmAnvil(player, event);
            }
            return;
        }
        // 2) Otherwise, the drag-nametag-onto-gear gesture (bottom inventory only).
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (MenuClicks.clickedInventory(event) == null
                || MenuClicks.clickedInventory(event) != event.getView().getBottomInventory()) {
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
            return;
        }
        cursor.setAmount(cursor.getAmount() - 1); // a nametag is spent to begin the rename (refunded if aborted)
        event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
        player.updateInventory();
        if (anvilMode) {
            openAnvil(player, target.clone());
        } else {
            player.sendMessage(prompt); // 1.8.9 chat fallback
        }
    }

    /** Open the anvil rename GUI (with the target's clone in the input slot so its name pre-fills the field). */
    private void openAnvil(Player player, ItemStack preview) {
        service.markAnvil(player.getUniqueId());
        Scheduling.onEntity(player, () -> {
            player.closeInventory();
            NametagAnvil.open(player, service.anvilTitle(), preview);
        });
    }

    /** Read the anvil rename field and apply it to the captured target; then close the GUI. */
    private void confirmAnvil(Player player, InventoryClickEvent event) {
        String text = NametagAnvil.renameText(event.getView());
        if (text == null) {
            return; // nothing typed yet — leave the dialog open
        }
        service.endAnvil(player.getUniqueId());
        String message = service.complete(player, text); // parses &-colours, blacklist, locates target by identity
        Scheduling.onEntity(player, player::closeInventory);
        if (message != null) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onAnvilDrag(InventoryDragEvent event) {
        if (anvilMode && event.getWhoClicked() instanceof Player player
                && service.inAnvil(player.getUniqueId()) && NametagAnvil.isAnvil(event.getView())) {
            event.setCancelled(true); // no dragging items into our rename dialog
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!anvilMode || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!service.inAnvil(player.getUniqueId()) || !NametagAnvil.isAnvil(event.getView())) {
            return;
        }
        service.endAnvil(player.getUniqueId());
        // Closed without confirming → abort and return the nametag (deferred so the inventory has settled).
        Scheduling.onEntityLater(player, 1L, () -> service.cancel(player));
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent: the floor-stable chat-capture path (1.8.9 fallback)
    public void onChat(AsyncPlayerChatEvent event) {
        if (anvilMode) {
            return; // modern uses the anvil GUI, not chat capture
        }
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
        service.clear(event.getPlayer().getUniqueId()); // never reuse a stale capture after a relog
    }
}
