package feature.combat;

import engine.stores.ImmuneStore;
import feature.compat.Hands;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * The {@code IMMUNE} applier (§ combat-flags): cancels matching hits while a player holds a damage-type
 * immunity in the {@link ImmuneStore}. The effect arms it through the per-event sink; this reads it back
 * on the SEPARATE future-damage events, on the victim's own region thread (concurrent store, UUID-keyed —
 * Folia-safe). A faithful port of a Cosmic Enchants-style Immune.
 */
public final class ImmuneListener implements Listener {

    private final ImmuneStore store;
    private final LongSupplier nowTicks;

    public ImmuneListener(ImmuneStore store, LongSupplier nowTicks) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /** Melee weapon (SWORD/AXE) and projectile immunity — the damager-typed hits. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        long now = nowTicks.getAsLong();
        if (event.getDamager() instanceof Projectile) {
            if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.PROJECTILE, now)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof LivingEntity attacker && attacker.getEquipment() != null) {
            String held = Hands.mainHand(attacker).getType().name();
            if (held.endsWith("_SWORD") && store.isImmune(victim.getUniqueId(), ImmuneStore.Type.SWORD, now)) {
                event.setCancelled(true);
            } else if (held.endsWith("_AXE") && store.isImmune(victim.getUniqueId(), ImmuneStore.Type.AXE, now)) {
                event.setCancelled(true);
            }
        }
    }

    /** Potion/magic immunity (and any pure-projectile cause that arrives as a plain damage event). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player victim)) {
            return; // entity-on-entity handled above; this is environmental/effect damage
        }
        long now = nowTicks.getAsLong();
        switch (event.getCause()) {
            case MAGIC, POISON, WITHER -> {
                if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.POTION, now)) {
                    event.setCancelled(true);
                }
            }
            case PROJECTILE -> {
                if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.PROJECTILE, now)) {
                    event.setCancelled(true);
                }
            }
            default -> {
                // Other causes: cancelled only by a blanket Type.ALL immunity.
                if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.ALL, now)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
