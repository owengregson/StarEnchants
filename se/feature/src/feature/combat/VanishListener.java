package feature.combat;

import engine.sink.EngineDamage;
import engine.sink.PlayerVisibility;
import engine.stores.VanishStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import platform.sched.Scheduling;

/**
 * The two halves of a {@code VANISH} window the sink cannot own: the landed-hit counter that breaks it early,
 * and the join re-sync that stops a relog from beating it.
 *
 * <p>{@code MONITOR} + {@code ignoreCancelled}, the {@link RageStacksListener} placement — only a hit that
 * actually LANDED gives a hidden body away, and only the ATTACK side is read, which is what makes taking damage
 * while vanished free. Engine-issued damage (DoT ticks, reflects) and a same-swing re-hit are excluded for the
 * same reason rage excludes them: neither is a swing the subject chose to make.
 *
 * <p>Runs on the firing region's thread; every visibility write hops to the VIEWER first, so the per-connection
 * hidden set is only ever touched by its owner.
 */
public final class VanishListener implements Listener {

    private final VanishStore vanish;
    private final PlayerVisibility visibility;
    private final LongSupplier nowTicks;

    public VanishListener(VanishStore vanish, PlayerVisibility visibility, LongSupplier nowTicks) {
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (EngineDamage.active() || ReHitGuard.skipped(event)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = shooterOf(event.getDamager());
        if (attacker == null || attacker == victim) {
            return; // self-inflicted damage is not aggression against anyone
        }
        VanishStore.Window exhausted = vanish.spendHit(attacker.getUniqueId(), nowTicks.getAsLong());
        if (exhausted != null) {
            exhausted.restore().run();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        long now = nowTicks.getAsLong();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joiner.getUniqueId())) {
                continue;
            }
            if (vanish.vanished(online.getUniqueId(), now)) {
                Scheduling.onEntity(joiner, () -> visibility.setVisible(joiner, online, false));
            }
        }
    }

    /** The player behind a hit: the damager itself, or a projectile's shooter — a shot gives you away too. */
    private static Player shooterOf(Entity damager) {
        if (damager instanceof Player direct) {
            return direct;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                ? shooter : null;
    }
}
