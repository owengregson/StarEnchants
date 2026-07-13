package feature.combat;

import engine.sink.LockedPotions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Modern (1.13+) potion-lock guard — the era-exclusive {@code overlay/modern} source (ADR-0044): while a type is
 * locked on an entity ({@link LockedPotions}, written by the {@code POTION_LOCK} effect) this CANCELS every
 * re-application, so a passive driver re-asserting the buff each tick can never make it stick. This is the genuine
 * prevention that a same-tick re-strip could not achieve — the strip always lost one tick to the driver, so the
 * effect flashed in and out (the grim-overhealth / druid-Speed-lock glitch). 1.8.9 has no
 * {@link EntityPotionEffectEvent}, so its binding is inert ({@code NoopListener}) and the Sink's per-tick re-strip
 * is the sole enforcer there.
 *
 * <p>Only an effect BECOMING/STAYING present is denied (ADDED / CHANGED / REFRESH); removals (CLEARED / REMOVED /
 * EXPIRED) pass through, so the Sink's own strip and a natural expiry are never fought. Folia-correct: the event
 * fires on the affected entity's owning region thread, and {@link LockedPotions} is a {@code ConcurrentHashMap}.
 */
public final class PotionLockListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        switch (event.getAction()) {
            case ADDED, CHANGED, REFRESH -> {
                PotionEffectType type = event.getModifiedType();
                if (type != null && LockedPotions.isLocked(event.getEntity().getUniqueId(), type.getName())) {
                    event.setCancelled(true); // deny the (re)application for the lock window
                }
            }
            default -> {
                // CLEARED / REMOVED / EXPIRED and any future removal action — never fought.
            }
        }
    }
}
