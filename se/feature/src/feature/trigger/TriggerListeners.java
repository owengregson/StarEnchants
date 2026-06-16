package feature.trigger;

import engine.run.ActivationContext;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Maps the non-combat Bukkit events to {@link TriggerDispatch} (docs/architecture.md §3.3): a block
 * break → MINE, a kill → KILL, a right/left interact → INTERACT(+direction), and environmental
 * fall/fire damage → FALL/FIRE. Each handler runs on its firing region thread; the dispatch reads
 * the actor's pre-resolved WornState and routes world mutations through the Sink, so no handler
 * touches a cross-region entity directly.
 */
public final class TriggerListeners implements Listener {

    private final TriggerDispatch dispatch;

    public TriggerListeners(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        dispatch.fire(player, dispatch.mine,
                new ActivationContext(player, null, null, event.getBlock().getLocation()), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            // The victim is dying; reading its location is region-safe (the death fires on its region).
            dispatch.fire(killer, dispatch.kill,
                    new ActivationContext(killer, event.getEntity(), null, event.getEntity().getLocation()), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // one fire per interaction — the off-hand pass is a duplicate of the same click
        }
        Player player = event.getPlayer();
        ActivationContext context = new ActivationContext(player, null, null, player.getLocation());
        dispatch.fire(player, dispatch.interact, context, event);
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            dispatch.fire(player, dispatch.interactLeft, context, event);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            dispatch.fire(player, dispatch.interactRight, context, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return; // entity-on-entity combat is CombatListener's job (ATTACK/DEFENSE)
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getCause()) {
            case FALL -> dispatch.fireDamage(player, dispatch.fall, self(player), event);
            case FIRE, FIRE_TICK, LAVA -> dispatch.fireDamage(player, dispatch.fire, self(player), event);
            default -> { } // other environmental causes carry no trigger yet
        }
    }

    private static ActivationContext self(Player player) {
        return new ActivationContext(player, null, null, player.getLocation());
    }
}
