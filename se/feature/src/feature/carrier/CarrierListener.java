package feature.carrier;

import feature.compat.Sounds;
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
 * The carrier application UX (ADR-0016): a carrier on the CURSOR clicked onto a target item applies it.
 * Bukkit-thin glue — logic is in {@link CarrierService}. Folia-correct: {@code InventoryClickEvent} fires
 * on the clicking player's own region thread, so mutating their cursor/inventory is in-thread.
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
        // Plain LEFT/RIGHT onto the player's OWN inventory grid only — never shift/number/double clicks
        // (dupe + collect misfires) nor another container's slots (would touch a region-owned inventory
        // on Folia).
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (codec.read(cursor) == null) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR) {
            return;
        }
        // Carrier-onto-carrier is only meaningful for dust onto a content book (ADR-0019); any other such
        // pairing falls through to the vanilla click rather than being swallowed as a cancelled no-op.
        if (codec.read(target) != null && !service.canCombineDust(cursor, target)) {
            return;
        }

        event.setCancelled(true); // we own this interaction now
        CarrierResult result = service.applyTo(cursor, target);
        if (result.consumed()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(target.getAmount() <= 0 ? null : target);
            player.updateInventory();
            // §I dust apply-feedback: in-thread, the event fires on the player's own region thread.
            if (result.sound() != null && !result.sound().isBlank()) {
                Sounds.play(player, player.getLocation(), result.sound(), 1.0f, 1.0f);
            }
            particles.spawn(player, result.particles(), 1);
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
