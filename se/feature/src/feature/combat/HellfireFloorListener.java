package feature.combat;

import engine.sink.OwnerZones;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Suppresses MAGMA_BLOCK burn (the {@code HOT_FLOOR} damage cause) for the entity a wearer-owned hellfire zone
 * was laid under — devil's Hell's Kitchen lays a magma floor that is meant to look infernal, not to chip the
 * struck victim's health. The immunity is scoped to that victim only ({@link OwnerZones#immuneFor}), so an ally,
 * bystander, or the wearer cannot surf a proc'd cylinder over natural magma or their own traps. The
 * {@link OwnerZones} cylinder is staked over the floor and expires with it. Only HOT_FLOOR is touched; other
 * fire / lava damage is left alone.
 *
 * <p>The cause is matched by NAME, not the {@code DamageCause.HOT_FLOOR} constant, so this same class compiles
 * against the 1.8.9 API (which predates magma blocks and the constant) — there, the cause simply never occurs.
 */
public final class HellfireFloorListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHotFloor(EntityDamageEvent event) {
        if (!"HOT_FLOOR".equals(event.getCause().name())) {
            return;
        }
        if (OwnerZones.immuneFor(event.getEntity().getUniqueId(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }
}
