package feature.mask;

import engine.stores.WardStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import platform.lang.Messages;

/**
 * The INVSEE ward guard (ADR-0053 §7/§8): another player opening a warded player's OWN inventory is cancelled.
 * Plugin-agnostic by construction — Essentials and CMI (and any other) {@code /invsee} both open the target's
 * real {@link org.bukkit.inventory.PlayerInventory}, whose holder IS the owning {@link Player}, so matching on
 * the holder catches every implementation without naming the plugin. Runs on the viewer's own region thread
 * (its open event). No config knob: an {@code InventoryOpenEvent} cancel needs no owned interception (cf.
 * {@code NearGuard}).
 */
public final class InvseeGuard implements Listener {

    private final WardStore wards;
    private final LongSupplier nowTicks;
    private final Messages messages;

    public InvseeGuard(WardStore wards, LongSupplier nowTicks, Messages messages) {
        this.wards = Objects.requireNonNull(wards, "wards");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        // Cheapest reject: only a Player-owned inventory viewed BY a player can be an invsee. A null holder
        // (chests, custom GUIs) and a non-player viewer fall out here.
        if (!(holder instanceof Player owner) || !(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (owner.getUniqueId().equals(viewer.getUniqueId())) {
            return; // the owner opening their own inventory is never the target of the ward
        }
        if (wards.isWarded(owner.getUniqueId(), WardStore.Type.INVSEE, nowTicks.getAsLong())) {
            event.setCancelled(true);
            messages.sendText(viewer, messages.format("mask.invsee-blocked", "NAME", owner.getName()));
        }
    }
}
