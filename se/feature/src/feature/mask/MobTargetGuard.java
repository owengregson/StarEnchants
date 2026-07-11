package feature.mask;

import engine.stores.WardStore;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * The MOB_TARGET ward guard (ADR-0053 §7): a warded player is invisible to mob aggro UNLESS they provoked the
 * mob. The {@code WARD} effect arms the flag through the sink; this reads it back on the SEPARATE targeting
 * event, on the mob's own region thread (concurrent stores, UUID-keyed — Folia-safe). Provocation is fed from
 * the MONITOR damage listener below, so a wearer who swings first still gets fought back.
 */
public final class MobTargetGuard implements Listener {

    private final WardStore wards;
    private final MaskProvocationStore provocations;
    private final LongSupplier nowTicks;

    public MobTargetGuard(WardStore wards, MaskProvocationStore provocations, LongSupplier nowTicks) {
        this.wards = Objects.requireNonNull(wards, "wards");
        this.provocations = Objects.requireNonNull(provocations, "provocations");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    /** Cancel a mob acquiring a warded player as its target — unless the wearer provoked (or is retaliated on by) it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player target)) {
            return;
        }
        long now = nowTicks.getAsLong();
        if (!wards.isWarded(target.getUniqueId(), WardStore.Type.MOB_TARGET, now)) {
            return; // cheapest reject: no ward → vanilla targeting stands, no provocation lookup
        }
        UUID mob = event.getEntity().getUniqueId();
        // Retaliation IS provocation by definition: a mob that targets because the wearer just hit it lands
        // here as TARGET_ATTACKED_ENTITY — let it through, AND record it, so a later re-acquire for another
        // reason (CLOSEST_PLAYER after losing sight) stays allowed too. Reason compared by NAME (the Causes
        // idiom) so a version that renames/omits the constant can never break the compile or the guard.
        if ("TARGET_ATTACKED_ENTITY".equals(event.getReason().name())) {
            provocations.provoke(target.getUniqueId(), mob, now);
            return;
        }
        if (!provocations.isProvoked(target.getUniqueId(), mob, now)) {
            event.setCancelled(true);
        }
    }

    /**
     * Record provocation whenever a warded player lands a hit on a mob (directly or via a projectile they
     * shot), so that mob may keep targeting them. MONITOR + {@code ignoreCancelled} — only hits that actually
     * connected provoke. Cheapest reject first: the store never grows for a maskless attacker (no MOB_TARGET
     * ward → nothing ever consults provocation for them).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
            return; // only aggression against a mob provokes; player victims are handled by other guards
        }
        Player attacker = shooterPlayer(event);
        if (attacker == null) {
            return;
        }
        long now = nowTicks.getAsLong();
        if (!wards.isWarded(attacker.getUniqueId(), WardStore.Type.MOB_TARGET, now)) {
            return; // maskless attacker — no ward reads provocation, so record nothing
        }
        provocations.provoke(attacker.getUniqueId(), victim.getUniqueId(), now);
    }

    /**
     * The attacking Player behind a hit: the direct damager, or the player who launched the projectile. No
     * shared helper exists in {@code feature/combat} — the same inline shape is duplicated across
     * {@code CombatDispatch}, {@code TeleblockListener} and {@code PetSummonListener}; mirrored here.
     */
    private static Player shooterPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
