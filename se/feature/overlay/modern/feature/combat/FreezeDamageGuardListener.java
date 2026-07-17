package feature.combat;

import engine.sink.FreezeLock;
import engine.sink.FrozenTargets;
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
 * <p>Also reconciles a CRASH-stranded freeze lock on join: Paper persists {@code freezeLocked} to
 * playerdata, and only the quit drain unlocks on a clean logout — a locked player with no live
 * window is unlocked and thawed here. Folia-correct: both events fire on the entity's owning thread.
 */
public final class FreezeDamageGuardListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (FrozenTargets.isFrozen(event.getEntity().getUniqueId(), System.currentTimeMillis())) {
            event.setCancelled(true); // the engine DoT is the only damage source for the window
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!FrozenTargets.isFrozen(player.getUniqueId(), System.currentTimeMillis())
                && FreezeLock.isLocked(player)) {
            FreezeLock.lock(player, false);
            player.setFreezeTicks(0);
        }
    }
}
