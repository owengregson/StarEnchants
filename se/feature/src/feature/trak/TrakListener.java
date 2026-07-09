package feature.trak;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Hands;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Trak-gem glue (§I); logic lives in {@link TrakService}. The apply gesture is a thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041); block breaks and kills feed the background lifetime counters, each
 * firing on the acting player's own region thread (Folia-correct).
 */
public final class TrakListener extends ApplyGestureListener {

    private final TrakService service;
    private final Hands hands;
    private final ShotWeapons shotWeapons;

    public TrakListener(TrakService service, Messages messages, Sounds sounds, Hands hands, ShotWeapons shotWeapons) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.shotWeapons = Objects.requireNonNull(shotWeapons, "shotWeapons");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isTrakGem(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.trackBlockBreak(event.getPlayer());
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // player deaths arrive via PlayerDeathEvent (its own handler list)
        }
        credit(event.getEntity(), false);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        credit(event.getEntity(), true);
    }

    /**
     * Credit the weapon that dealt the KILLING BLOW (§I F19), resolved from the fatal damage event — not
     * whatever is held when the victim dies. A non-combat finish (fall/lava after a hit, so getKiller lingers)
     * or a potion/TNT/pet kill resolves to no weapon and credits nothing.
     */
    private void credit(LivingEntity dead, boolean victimIsPlayer) {
        Player killer = dead.getKiller();
        if (killer == null) {
            return;
        }
        EntityDamageEvent last = dead.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent fatal)) {
            return; // hit-then-environment death: the fatal blow was not an entity, so no weapon to credit
        }
        ItemStack weapon = fatalWeapon(fatal, killer);
        if (weapon != null) {
            service.trackKill(killer, victimIsPlayer, weapon);
        }
    }

    /** The item behind the fatal blow: the killer's main hand for a melee hit, the launcher for their projectile. */
    private ItemStack fatalWeapon(EntityDamageByEntityEvent fatal, Player killer) {
        Entity damager = fatal.getDamager();
        if (damager == killer) {
            // Melee: the death event is nested in this same damage tick, and a melee blow implies
            // attacker/victim proximity — one owned region on Folia — so this main-hand read is region-correct.
            ItemStack main = hands.mainHand(killer);
            return main == null || main.getType() == Material.AIR ? null : main.clone();
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() == killer) {
            return shotWeapons.claim(projectile.getUniqueId()); // null for potions/snowballs/dispenser arrows
        }
        return null; // TNT, pets, anything else — no weapon to credit
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            service.trackFishCatch(event.getPlayer());
        }
    }
}
