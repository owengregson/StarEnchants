package feature.combat;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import engine.sink.FreezeLock;
import engine.sink.FrozenTargets;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Modern freeze guard (FREEZE, ADR-0065) — the era-exclusive {@code overlay/modern} source: while a
 * victim is inside a live frozen window, vanilla's own fully-frozen self-damage (1 per 40 ticks,
 * {@code aiStep} — NOT guarded by the freeze-tick lock) is cancelled so the engine DoT is the ONLY
 * damage source, uniform across victims (vanilla's is skipped for leather-wearers via canFreeze).
 * 1.8.9 has no {@code DamageCause.FREEZE}, so its binding is inert ({@code NoopListener}).
 *
 * <p>Also reconciles a STRANDED freeze lock — Paper persists {@code freezeLocked} to entity NBT and
 * only a window's teardown unlocks: a locked player with no live window is unlocked + thawed on
 * join (crash strand), and any other locked entity on world-add (unload strand — a freshly-loaded
 * instance can have no live window tasks, so any lock it carries is stale; players are excluded
 * because their instance and tasks survive world changes). Folia-correct: every event here fires on
 * the entity's owning thread.
 */
public final class FreezeDamageGuardListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (FrozenTargets.isFrozen(event.getEntity().getUniqueId())) {
            event.setCancelled(true); // the engine DoT is the only damage source for the window
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!FrozenTargets.isFrozen(player.getUniqueId()) && FreezeLock.isLocked(player)) {
            FreezeLock.lock(player, false);
            player.setFreezeTicks(0);
        }
    }

    @EventHandler
    public void onEntityLoad(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        if (FreezeLock.isLocked(entity)) {
            FreezeLock.lock(entity, false);
            entity.setFreezeTicks(0);
            FrozenTargets.clear(entity.getUniqueId()); // the entry's tasks died with the old instance
        }
    }
}
