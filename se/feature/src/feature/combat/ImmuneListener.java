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
import org.bukkit.event.player.PlayerFishEvent;

/**
 * The {@code IMMUNE} applier (§ combat-flags): cancels matching hits while a player holds a damage-type
 * immunity in the {@link ImmuneStore}. The effect arms it through the per-event sink; this reads it back
 * on the SEPARATE future-damage events, on the victim's own region thread (concurrent store, UUID-keyed —
 * Folia-safe). A faithful port of a Cosmic Enchants-style Immune.
 */
public final class ImmuneListener implements Listener {

    private final ImmuneStore store;
    private final LongSupplier nowTicks;
    private final Hands hands;

    public ImmuneListener(ImmuneStore store, LongSupplier nowTicks, Hands hands) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    /** Melee weapon (SWORD/AXE) and projectile immunity — the damager-typed hits. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        long now = nowTicks.getAsLong();
        // Fish-hook first: the bobber IS a Projectile, so the generic projectile arm below would otherwise
        // claim it under the wrong immunity type (ADR-0053 Fisherman).
        if (isFishHookTypeName(event.getDamager().getType().name())) {
            if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.FISHHOOK, now)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof Projectile) {
            if (store.isImmune(victim.getUniqueId(), ImmuneStore.Type.PROJECTILE, now)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof LivingEntity attacker && attacker.getEquipment() != null) {
            String held = hands.mainHand(attacker).getType().name();
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

    /** The reel: a FISHHOOK-immune player caught on a rod is never pulled (ADR-0053 Fisherman). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // getCaught() only — event.getHook()'s RETURN TYPE changed across the range (Fish → FishHook), so
        // calling it anywhere is a binary break on part of 1.8→26.x.
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
                && event.getCaught() instanceof Player caught
                && store.isImmune(caught.getUniqueId(), ImmuneStore.Type.FISHHOOK, nowTicks.getAsLong())) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether an {@code EntityType} name is the rod bobber — by NAME, never {@code instanceof FishHook} or an
     * {@code EntityType} constant: the class and constant were renamed across 1.8&rarr;26.x
     * (FISHING_HOOK &rarr; FISHING_BOBBER; legacy also reports FISH_HOOK), see cross-version-item-api.
     */
    static boolean isFishHookTypeName(String typeName) {
        return "FISHING_BOBBER".equals(typeName) || "FISHING_HOOK".equals(typeName)
                || "FISH_HOOK".equals(typeName);
    }
}
