package feature.combat;

import engine.sink.FallingBlockCasts;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Fires the {@code IMPACT} trigger when a tracked {@code FALLING_BLOCK} lands (druid's Terrablender grass, any
 * "debris" cosmetic): cancels the block placement (it never sticks), finds the player it landed on, and runs
 * the spawner's IMPACT abilities on them via {@link TriggerDispatch#fireImpact} — so the impact is fully
 * author-defined. A grid's many landings are deduped by a short cooldown on the IMPACT ability. The event fires
 * on the block's own region thread, co-region with the player it landed on, so the proximity read is Folia-safe.
 *
 * <p>Runs at {@link EventPriority#LOWEST} and does NOT {@code ignoreCancelled}: a cosmetic block must be removed
 * the instant it would touch down, BEFORE any region plugin (WorldGuard, spawn protection) gets the event — if
 * another plugin cancels the land first, our handler would be skipped, the entity would never be removed, and it
 * would rest on the ground re-firing the (cancelled) land every tick until its TTL: visually a placed block. By
 * intercepting first and force-removing, the block never becomes (or impersonates) a real block on any setup.
 */
public final class FallingBlockListener implements Listener {

    /** A landed block "hits" a player within this radius of where it settled. */
    private static final double HIT_RADIUS = 1.5;

    private final TriggerDispatch dispatch;

    public FallingBlockListener(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLand(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof FallingBlock) || !FallingBlockCasts.isTracked(entity.getUniqueId())) {
            return;
        }
        event.setCancelled(true); // a cosmetic block — never let it place
        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(entity.getUniqueId());
        entity.remove();
        if (cast == null || cast.owner() == null) {
            return; // an owner-less cosmetic block: cancelled + removed above, but no IMPACT to fire
        }
        Player owner = Bukkit.getPlayer(cast.owner());
        LivingEntity victim = playerNear(event.getBlock().getLocation(), owner);
        if (owner != null && victim != null) {
            dispatch.fireImpact(owner, victim, cast.damage());
        }
    }

    /** A player (other than {@code exclude}) within {@link #HIT_RADIUS} of where the block settled, or null. */
    private static LivingEntity playerNear(Location at, Player exclude) {
        Location center = at.clone().add(0.5, 0.5, 0.5);
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        // Region-bounded query (Folia-safe on the block's own region), not a world-wide cross-region scan.
        for (Entity e : world.getNearbyEntities(center, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
            if (e instanceof Player p && !p.equals(exclude)) {
                return p;
            }
        }
        return null;
    }
}
