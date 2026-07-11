package feature.pet;

import feature.compat.MenuClicks;
import item.codec.PetCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import platform.sched.Scheduling;

/**
 * Keeps {@code WornState} fresh as pets move through the HOTBAR (ADR-0052). Armour has a dedicated change
 * event; hotbar contents do not — so this observes the inventory mutations that can move a pet in or out
 * (click / drag / drop, all era-stable events) and requests the standard refresh next tick (the slot is
 * current only after the event returns — the equip-listener rule). Filtered cheaply: only gestures that
 * TOUCH a pet stack or a hotbar slot schedule anything. Item pickup has no era-stable event (1.8 lacks
 * {@code EntityPickupItemEvent}); the pets sweep (the module's boot timer) is the catch-all for that and
 * any other missed path.
 */
public final class PetHotbarListener implements Listener {

    private final PetCodec codec;
    private final Consumer<Player> refresh;
    private final BooleanSupplier enabled;

    public PetHotbarListener(PetCodec codec, Consumer<Player> refresh, BooleanSupplier enabled) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!enabled.getAsBoolean() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // A pet moved by this click, or a hotbar slot touched directly (number-key swaps included).
        boolean petMoved = codec.isPet(event.getCursor()) || codec.isPet(event.getCurrentItem());
        boolean hotbarTouched = event.getHotbarButton() >= 0
                || (event.getSlot() < 9
                        && MenuClicks.clickedInventory(event) == event.getView().getBottomInventory());
        if (petMoved || hotbarTouched) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled.getAsBoolean() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (codec.isPet(event.getOldCursor())) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (enabled.getAsBoolean() && codec.isPet(event.getItemDrop().getItemStack())) {
            scheduleRefresh(event.getPlayer());
        }
    }

    private void scheduleRefresh(Player player) {
        // The mutated slot is current only after the event returns — refresh next tick on the player's thread.
        Scheduling.onEntityLater(player, 1L, () -> refresh.accept(player));
    }
}
