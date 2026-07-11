package feature.pet;

import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import platform.sched.Scheduling;

/**
 * Enforces the ADR-0052 summon flags over the {@link PetSummons} registry. NO-TARGET: a tracked summon never
 * acquires a target (the Creeper pet's creeper is passive — it cannot swell at players on its own).
 * DETONATE-ON-PLAYER-HIT: a hit from a player (or a player's projectile) starts the fuse ONCE — modern
 * servers get the real swell via {@code Creeper#setIgnited} (reflected once per detonation; it is absent on
 * the 1.8 lane), the fallback a scheduled entity-safe explosion after the vanilla fuse. Damage stays
 * UNCANCELLED throughout, so knockback and the spawn-time 1024-health "infinite pool" behave like a normal
 * mob. Both handlers run on the summon's own region thread (its events).
 */
public final class PetSummonListener implements Listener {

    private static final Logger LOG = System.getLogger("StarEnchants.PetSummons");
    /** The vanilla creeper fuse (30 ticks) — the spec's "normal detonation time". */
    private static final long FUSE_TICKS = 30L;
    /** A charged creeper's explosion power (vanilla 6.0); entity damage only, never block damage. */
    private static final float CHARGED_POWER = 6.0f;

    private final BooleanSupplier enabled;

    public PetSummonListener(BooleanSupplier enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        SummonFlags flags = PetSummons.flags(event.getEntity().getUniqueId());
        if (flags != null && flags.noTarget()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Entity victim = event.getEntity();
        UUID id = victim.getUniqueId();
        SummonFlags flags = PetSummons.flags(id);
        if (flags == null || !flags.detonateOnPlayerHit() || !isPlayerHit(event.getDamager())) {
            return;
        }
        PetSummons.forget(id); // one fuse per summon — a second hit must not double-detonate
        if (!ignite(victim)) {
            // No setIgnited on this server (1.8 lane): the fuse is ours — explode entity-safe, then remove.
            Scheduling.onEntityLater(victim, FUSE_TICKS, () -> {
                if (victim.isValid()) {
                    victim.getWorld().createExplosion(victim.getLocation().getX(), victim.getLocation().getY(),
                            victim.getLocation().getZ(), CHARGED_POWER, false, false);
                    victim.remove();
                }
            });
        }
    }

    /** A direct player hit, or a projectile a player launched. */
    private static boolean isPlayerHit(Entity damager) {
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    /**
     * Start the real vanilla fuse when this server has {@code Creeper#setIgnited} (1.13+; reflected because
     * the shared tree also compiles against 1.8). The vanilla path swells, bangs and removes the creeper
     * itself, honouring the server's explosion rules. Returns whether it took.
     */
    private static boolean ignite(Entity victim) {
        if (!(victim instanceof Creeper)) {
            return false;
        }
        try {
            victim.getClass().getMethod("setIgnited", boolean.class).invoke(victim, true);
            return true;
        } catch (ReflectiveOperationException absent) {
            LOG.log(Level.DEBUG, "Creeper#setIgnited unavailable — using the scheduled-fuse fallback", absent);
            return false;
        }
    }
}
