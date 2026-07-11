package feature.pet;

import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import platform.sched.Scheduling;

/**
 * Enforces the ADR-0052 summon flags over the {@link PetSummons} registry. NO-TARGET: a tracked summon never
 * acquires a target (the Creeper pet's creeper is passive — it cannot swell at players on its own).
 * DETONATE-ON-PLAYER-HIT: a hit from a player (or a player's projectile) starts a one-shot scheduled fuse —
 * NEVER {@code Creeper#setIgnited}, whose real vanilla explosion griefs terrain wherever mobGriefing is on;
 * the explosion here is entity-damage-only on both eras. INVINCIBLE: damage events are ZEROED, never
 * cancelled, so the summon keeps hit animations and knockback like a normal mob while no burst — however
 * large in one tick — can kill it. All handlers run on the summon's own region thread (its events).
 */
public final class PetSummonListener implements Listener {

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

    // HIGHEST so the zeroing lands after every damage-modifying plugin: an INVINCIBLE summon takes the hit
    // (animation + knockback intact — cancelling would eat both) but the final amount is 0, so no one-tick
    // burst can out-damage it. Its own removal happens by TTL or the detonation fuse, never by damage.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        SummonFlags flags = PetSummons.flags(event.getEntity().getUniqueId());
        if (flags != null && flags.invincible()) {
            event.setDamage(0);
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
        // The fuse is ALWAYS ours, on both eras: Creeper#setIgnited would fire the REAL vanilla explosion,
        // which griefs terrain whenever mobGriefing is on — the guarantee here is entity damage only, never
        // block damage (setFire=false, breakBlocks=false).
        Scheduling.onEntityLater(victim, FUSE_TICKS, () -> {
            if (victim.isValid()) {
                victim.getWorld().createExplosion(victim.getLocation().getX(), victim.getLocation().getY(),
                        victim.getLocation().getZ(), CHARGED_POWER, false, false);
                victim.remove();
            }
        });
    }

    /** A direct player hit, or a projectile a player launched. */
    private static boolean isPlayerHit(Entity damager) {
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }
}
