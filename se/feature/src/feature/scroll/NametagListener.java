package feature.scroll;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;
import platform.sched.Scheduling;

/**
 * Item-nametag gesture glue (§I); logic lives in {@link NametagService}. A thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041) — dragging a nametag onto gear begins a rename. On modern servers
 * ({@link AnvilRename#supported()}) the rename is captured through a raw-Bukkit ANVIL GUI — the player types
 * the new name, legacy {@code &} colour codes are parsed, and the result-slot click applies it; the 1.8.9 fork
 * falls back to chat capture. The anvil dialog lock runs in {@link #handledBeforeGesture}, ahead of the
 * drag-onto-gear gesture, so a click inside our rename GUI is never re-interpreted.
 *
 * <p>Folia-correct: inventory events fire on the clicking player's own region thread; {@code AsyncPlayerChatEvent}
 * is async, so its mutation is hopped back.
 */
public final class NametagListener extends ApplyGestureListener {

    private final NametagService service;
    private final AnvilRename anvilRename; // §4 era seam: real anvil rename (modern) vs chat capture (1.8)
    private final boolean anvilMode;

    public NametagListener(NametagService service, Messages messages, Sounds sounds, AnvilRename anvilRename) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
        this.anvilRename = Objects.requireNonNull(anvilRename, "anvilRename");
        this.anvilMode = anvilRename.supported();
    }

    // A click inside OUR anvil rename GUI: lock the dialog; the result slot confirms the rename. Runs BEFORE
    // the drag-onto-gear gesture (a shift-click inside the rename anvil must stay cancelled).
    @Override
    @SuppressWarnings("deprecation") // getView: the floor-stable view path
    protected boolean handledBeforeGesture(Player player, InventoryClickEvent event) {
        if (anvilMode && service.inAnvil(player.getUniqueId()) && anvilRename.isAnvil(event.getView())) {
            event.setCancelled(true); // typing renames; no item may be pulled out
            if (event.getRawSlot() == AnvilRename.RESULT_SLOT) {
                confirmAnvil(player, event);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isNametag(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        // One nametag renames one item — cost parity with every other apply family (a stack must be split first).
        if (target.getAmount() > 1) {
            return GestureOutcome.noop(messages.format("common.single-item"));
        }
        // begin() refuses (null) when a rename is already pending, so a second nametag isn't consumed for nothing.
        String prompt = service.begin(player.getUniqueId(), target);
        if (prompt == null) {
            return GestureOutcome.noop(service.busyMessage()); // §L lang.yml scroll.nametag.busy
        }
        cursor.setAmount(cursor.getAmount() - 1); // a nametag is spent to begin the rename (refunded if aborted)
        ItemStack preview = target.clone();
        return anvilMode
                ? GestureOutcome.consumedOnly(null).andThen(() -> openAnvil(player, preview))
                : GestureOutcome.consumedOnly(prompt); // 1.8.9 chat fallback
    }

    /** Open the anvil rename GUI (with the target's clone in the input slot so its name pre-fills the field). */
    private void openAnvil(Player player, ItemStack preview) {
        service.markAnvil(player.getUniqueId());
        Scheduling.onEntity(player, () -> {
            player.closeInventory();
            anvilRename.open(player, service.anvilTitle(), preview);
        });
    }

    /** Read the anvil rename field and apply it to the captured target; then close the GUI. */
    @SuppressWarnings("deprecation") // getView: the floor-stable view path
    private void confirmAnvil(Player player, InventoryClickEvent event) {
        String text = anvilRename.renameText(event.getView());
        if (text == null) {
            return; // nothing typed yet — leave the dialog open
        }
        service.endAnvil(player.getUniqueId());
        String message = service.complete(player, text); // parses &-colours, blacklist, locates target by identity
        event.getView().getTopInventory().clear(); // §I drop the display clone so the real anvil's close returns no dupe
        Scheduling.onEntity(player, player::closeInventory);
        if (message != null) {
            messages.sendText(player, message);
        }
    }

    @EventHandler
    public void onAnvilDrag(InventoryDragEvent event) {
        if (anvilMode && event.getWhoClicked() instanceof Player player
                && service.inAnvil(player.getUniqueId()) && anvilRename.isAnvil(event.getView())) {
            event.setCancelled(true); // no dragging items into our rename dialog
        }
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!anvilMode || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!service.inAnvil(player.getUniqueId()) || !anvilRename.isAnvil(event.getView())) {
            return;
        }
        // §I Drop the display clone BEFORE vanilla returns the anvil input (the close event fires first), so an
        // abort never duplicates the item; the real gear stayed in the player's inventory the whole time.
        event.getView().getTopInventory().clear();
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
                messages.sendText(player, message);
            }
        });
    }

}
