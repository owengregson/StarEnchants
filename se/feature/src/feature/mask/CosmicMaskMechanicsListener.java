package feature.mask;

import engine.effect.kind.ActiveMasks;
import engine.sink.CombatTag;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.projectiles.ProjectileSource;
import platform.resolve.RegistryResolvers;

/** Exact event-backed mechanics for Cosmic masks that cannot be expressed as ordinary worn abilities. */
public final class CosmicMaskMechanicsListener implements Listener {

    private static final String BOSS = "masks/boss-mask";
    private static final String DEATH_KNIGHT = "masks/death-knight";
    private static final String DRAGON = "masks/dragon-mask";
    private static final String JOKER = "masks/joker-mask";
    private static final String MONOPOLY = "masks/monopoly-mask";
    private static final String PARTY_HAT = "masks/party-hat";
    private static final String PILGRIM = "masks/pilgrim-mask";
    private static final String PURGE = "masks/purge-mask";
    private static final String SCARECROW = "masks/scarecrow-mask";
    private static final String TURKEY = "masks/turkey-mask";

    private final BooleanSupplier enabled;
    private final DoubleSupplier random;
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final int cloud;

    public CosmicMaskMechanicsListener(BooleanSupplier enabled, SinkFactory sinks, SinkEnv env,
                                       RegistryResolvers resolvers) {
        this(enabled, sinks, env, resolvers, () -> ThreadLocalRandom.current().nextDouble());
    }

    CosmicMaskMechanicsListener(BooleanSupplier enabled, SinkFactory sinks, SinkEnv env,
                                RegistryResolvers resolvers, DoubleSupplier random) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.random = Objects.requireNonNull(random, "random");
        this.cloud = Objects.requireNonNull(resolvers, "resolvers").particle("CLOUD").orElse(-1);
    }

    /**
     * Cosmic's general mask hit hook runs only for entity-on-entity damage. Outgoing multipliers require a
     * direct Player damager (projectile shooters do not count); incoming dodge/reduction accepts every entity
     * damager. Multi-Masks intentionally receive the same mechanics through StarEnchants' universal likeness.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamageOther(EntityDamageByEntityEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        if (event.getDamager() instanceof Player damager) {
            if (ActiveMasks.has(damager, PURGE)) {
                event.setDamage(event.getDamage() * 1.025);
            }
            if (ActiveMasks.has(damager, DEATH_KNIGHT)) {
                event.setDamage(event.getDamage() * 1.025);
            }
            if (ActiveMasks.has(damager, DRAGON)) {
                event.setDamage(event.getDamage() * 1.05);
            }
            if (ActiveMasks.has(damager, PARTY_HAT)) {
                event.setDamage(event.getDamage() * 1.04);
            }
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (random.getAsDouble() <= 0.02 && ActiveMasks.has(victim, TURKEY)) {
            event.setCancelled(true);
            SinkReadback sink = sinks.create(env);
            sink.message(victim, "&e&l* DODGED [&7Turkey Mask&e&l]");
            if (cloud >= 0) {
                sink.particle(victim.getLocation().add(0.0, 0.5, 0.0),
                        cloud, 10, -1, 0.2, 0.2, 0.2, 0.1);
            }
            sink.flush();
        } else {
            if (ActiveMasks.has(victim, MONOPOLY)) {
                event.setDamage(event.getDamage() * 0.95);
            }
            if (ActiveMasks.has(victim, PARTY_HAT)) {
                event.setDamage(event.getDamage() * 0.95);
            }
        }
    }

    /**
     * Joker Mask resets the wearer's own combat tag three seconds shorter and the opposing player's four
     * seconds longer. Both bonuses compose when both players wear Joker: 15 - 3 + 4 = 16 seconds each.
     * Projectile hits belong to their player shooter, matching the main combat dispatch.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJokerCombatTag(EntityDamageByEntityEvent event) {
        if (!enabled.getAsBoolean() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (!(source instanceof Player attacker) || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        boolean attackerJoker = ActiveMasks.has(attacker, JOKER);
        boolean victimJoker = ActiveMasks.has(victim, JOKER);
        if (!attackerJoker && !victimJoker) {
            return;
        }

        boolean enemies = !feature.combat.CombatDispatch.friendly(attacker, victim);
        CombatTag.tagFor(attacker.getUniqueId(), jokerTagDuration(attackerJoker, victimJoker && enemies));
        CombatTag.tagFor(victim.getUniqueId(), jokerTagDuration(victimJoker, attackerJoker && enemies));
    }

    static long jokerTagDuration(boolean wearer, boolean opposingJoker) {
        long duration = CombatTag.WINDOW_MS;
        if (wearer) {
            duration -= 3_000L;
        }
        if (opposingJoker) {
            duration += 4_000L;
        }
        return duration;
    }

    /** Boss Mask: +10% outgoing boss damage, otherwise -25% incoming boss damage. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (!(shooter instanceof Entity entity)) {
                return;
            }
            damager = entity;
        }
        if (event.getEntity().hasMetadata("boss")) {
            if (damager instanceof Player player && ActiveMasks.has(player, BOSS)) {
                event.setDamage(event.getDamage() * 1.1);
            }
        } else if (damager.hasMetadata("boss")
                && event.getEntity() instanceof Player player && ActiveMasks.has(player, BOSS)) {
            event.setDamage(event.getDamage() * 0.75);
        }
    }

    /** Scarecrow cancels food losses only; food gains remain untouched. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLoss(FoodLevelChangeEvent event) {
        if (enabled.getAsBoolean() && event.getEntity() instanceof Player player
                && event.getFoodLevel() < player.getFoodLevel() && ActiveMasks.has(player, SCARECROW)) {
            event.setCancelled(true);
        }
    }

    /** Pilgrim multiplies non-player mob-death XP by 1.25, truncating to an integer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (enabled.getAsBoolean() && !(event.getEntity() instanceof Player)
                && killer != null && ActiveMasks.has(killer, PILGRIM)) {
            event.setDroppedExp((int) (event.getDroppedExp() * 1.25));
        }
    }
}
