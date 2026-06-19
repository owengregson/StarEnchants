package feature.carrier;

import feature.fx.ParticleFx;
import item.codec.CarrierCodec;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The carrier application UX (ADR-0016): holding a carrier on the CURSOR and clicking a target item
 * applies it (the standard "drag the book onto the item"). Bukkit-thin glue — all logic is in
 * {@link CarrierService}; this only recognises the gesture, cancels the vanilla click, drives the
 * apply, and commits the mutated cursor/slot. Folia-correct: an {@code InventoryClickEvent} fires on
 * the clicking player's own region thread, so mutating that player's cursor/inventory is in-thread.
 */
public final class CarrierListener implements Listener {

    private final CarrierService service;
    private final CarrierCodec codec;
    private final ParticleFx particles;

    public CarrierListener(CarrierService service, CarrierCodec codec) {
        this(service, codec, ParticleFx.NONE);
    }

    public CarrierListener(CarrierService service, CarrierCodec codec, ParticleFx particles) {
        this.service = service;
        this.codec = codec;
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Only a plain LEFT/RIGHT place onto a slot in the player's OWN inventory grid — never shift/
        // number/double clicks (dupe + double-click-collect misfires) nor another container/GUI's slots
        // (so we never touch a chest or a region-owned inventory on Folia).
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (codec.read(cursor) == null) {
            return; // the cursor is not a carrier — leave the click alone
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR) {
            return; // no target
        }
        // A carrier-onto-carrier gesture is only meaningful for dust onto a content book (ADR-0019); any
        // other carrier dropped onto a carrier (a book onto a book, a dust onto a scroll/dust) is left to
        // the vanilla click rather than swallowed as a dead, cancelled no-op.
        if (codec.read(target) != null && !service.canCombineDust(cursor, target)) {
            return;
        }

        event.setCancelled(true); // we own this interaction now
        CarrierResult result = service.applyTo(cursor, target);
        if (result.consumed()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(target.getAmount() <= 0 ? null : target);
            player.updateInventory();
            // §I apply-feedback (dust): play on the player's own region thread — the event fires there.
            if (result.sound() != null && !result.sound().isBlank()) {
                player.playSound(player.getLocation(), result.sound(), 1.0f, 1.0f);
            }
            particles.spawn(player, result.particles(), 1);
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
