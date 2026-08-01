package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.selector.kind.Allies;
import engine.sink.GuardianCasts;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import org.bukkit.util.Vector;

/** Exact intended implementations of Cosmic Spirits and Undead Ruse. */
public final class CosmicArmorSummonListener implements Listener {

    private static final String SPIRITS = "enchants/spirits";
    private static final String UNDEAD_RUSE = "enchants/undead-ruse";

    private record Spirit(UUID owner, int level, long expiresAt) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final LongSupplier nowTicks;
    private final Map<UUID, Spirit> spirits = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> undeadOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> summons = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spiritCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spiritHealCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> spiritFireballs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> relaunchingSpiritFireballs = ConcurrentHashMap.newKeySet();
    private final int spell;
    private final int flame;
    private final int heart;
    private final int witchMagic;
    private final int ironGolemDeath;
    private final int orbPickup;

    public CosmicArmorSummonListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                                     RegistryResolvers resolvers, LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        spell = resolvers.particle("SPELL").orElse(-1);
        flame = resolvers.particle("FLAME").orElse(-1);
        heart = resolvers.particle("HEART").orElse(-1);
        witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
        ironGolemDeath = resolvers.sound("IRONGOLEM_DEATH").orElse(-1);
        orbPickup = resolvers.sound("ORB_PICKUP").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer) || !(event.getDamager() instanceof Player attacker)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CombatDispatch.friendly(wearer, attacker)
                || !protection.allows(attacker.getUniqueId(), wearer.getLocation())
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int spiritsLevel = EnchantLevels.worn(wearer, SPIRITS);
        if (spiritsLevel > 0) {
            trySpirits(wearer, spiritsLevel);
        }
        int undeadLevel = EnchantLevels.worn(wearer, UNDEAD_RUSE);
        if (undeadLevel > 0 && ThreadLocalRandom.current().nextDouble() < Math.min(0.04, undeadLevel * 0.01)) {
            spawnUndeadRuse(wearer, attacker, undeadLevel);
        }
    }

    private void trySpirits(Player wearer, int level) {
        long now = nowTicks.getAsLong();
        UUID wearerId = wearer.getUniqueId();
        if (spiritCooldown.getOrDefault(wearerId, 0L) > now
                || ThreadLocalRandom.current().nextDouble() >= spiritChanceForLevel(level)) {
            return;
        }
        spiritCooldown.put(wearerId, now + 200L);
        Location at = wearer.getLocation().clone().add(0.0, 1.0, 0.0);
        SinkReadback opening = sinks.create(env);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (spell >= 0) {
            opening.particle(at, spell, 45, -1, random.nextDouble(), random.nextDouble(),
                    random.nextDouble(), 0.64);
        }
        if (flame >= 0) {
            opening.particle(at, flame, 35, -1, random.nextDouble(), random.nextDouble(),
                    random.nextDouble(), 0.154);
        }
        opening.flush();

        int count = level / 10 + 1;
        for (int i = 0; i < count; i++) {
            if (at.getChunk().getEntities().length >= 50) {
                continue;
            }
            Blaze blaze = at.getWorld().spawn(at, Blaze.class);
            blaze.setCustomName("§c§l" + wearer.getName() + "'s Spirit");
            blaze.setCustomNameVisible(true);
            blaze.setCanPickupItems(false);
            double health = 50.0 + level * 10.0;
            blaze.setMaxHealth(health);
            blaze.setHealth(health);
            blaze.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
            if (level >= 4) blaze.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
            if (level >= 6) blaze.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0));
            if (level >= 8) blaze.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
            if (level == 10) blaze.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0));
            Spirit spirit = new Spirit(wearerId, level, now + 200L);
            UUID summonId = blaze.getUniqueId();
            spirits.put(summonId, spirit);
            summons.put(summonId, blaze);
            GuardianCasts.bind(summonId, wearerId, GuardianCasts.Kind.ANCESTRAL,
                    () -> forgetSummon(summonId));
            if (ironGolemDeath >= 0) {
                SinkReadback sound = sinks.create(env);
                sound.sound(blaze.getLocation(), ironGolemDeath, 1.0f, 0.55f);
                sound.flush();
            }
            int interval = spiritHealInterval(level);
            Scheduling.onEntityLater(blaze, interval, () -> pulse(blaze, spirit, interval));
            Scheduling.onEntityLater(blaze, 200L, () -> remove(blaze));
        }
    }

    static double spiritChanceForLevel(int level) {
        return Math.min(0.20, 0.01 + level * 0.05);
    }

    static int spiritHealInterval(int level) {
        return (int) ((20 + (10 - level) * 4) * 1.5);
    }

    static int spiritHealAmount(int level) {
        return level <= 5 ? 1 : 2;
    }

    static int spiritMaxHealTargets(int level) {
        return level > 6 ? 2 : 1;
    }

    static int undeadInvisibilityTicks(int spawned) {
        return spawned * 20 + 20;
    }

    private void pulse(Blaze blaze, Spirit spirit, int interval) {
        long now = nowTicks.getAsLong();
        if (!blaze.isValid() || now >= spirit.expiresAt()
                || spirits.get(blaze.getUniqueId()) != spirit) {
            return;
        }
        Player owner = Bukkit.getPlayer(spirit.owner());
        if (owner != null) {
            int radius = 8 + spirit.level();
            int maxTargets = spiritMaxHealTargets(spirit.level());
            List<Player> heal = new ArrayList<>();
            for (Entity entity : blaze.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Player player
                        && !player.isDead()
                        && player.getHealth() < player.getMaxHealth()
                        && spiritHealCooldown.getOrDefault(player.getUniqueId(), 0L) <= now
                        && (player.getUniqueId().equals(spirit.owner()) || Allies.allied(owner, player))) {
                    heal.add(player);
                    spiritHealCooldown.put(player.getUniqueId(), now + interval);
                    if (heal.size() >= maxTargets) break;
                }
            }
            SinkReadback pulse = sinks.create(env);
            if (heart >= 0) {
                pulse.particle(blaze.getLocation().clone().add(0.0, 1.0, 0.0), heart, 20,
                        -1, 0.5, 0.5, 0.5, 0.01);
            }
            pulse.flush();
            int amount = spiritHealAmount(spirit.level());
            for (Player player : heal) {
                Scheduling.onEntity(player, () -> heal(player, amount));
            }
        }
        Scheduling.onEntityLater(blaze, interval, () -> pulse(blaze, spirit, interval));
    }

    private void heal(Player player, int amount) {
        if (player.isDead() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.heal(player, amount);
        if (orbPickup >= 0) sink.privateSound(player, orbPickup, 0.3f, 1.4f);
        if (heart >= 0) {
            sink.particle(player.getLocation().clone().add(0.0, 1.0, 0.0), heart, 15,
                    -1, 0.75, 0.75, 0.75, 0.01);
        }
        sink.flush();
    }

    @SuppressWarnings("deprecation")
    private void spawnUndeadRuse(Player wearer, Player attacker, int level) {
        int count = (int) (ThreadLocalRandom.current().nextDouble() / 2.0 * level) + 1;
        Location spawn = wearer.getTargetBlock((java.util.Set<Material>) null, 5).getLocation().add(0.0, 1.0, 0.0);
        SinkReadback opening = sinks.create(env);
        if (witchMagic >= 0) {
            opening.particle(spawn, witchMagic, 20, -1, Math.random(), Math.random(), Math.random(), 0.5);
            opening.particle(wearer.getLocation(), witchMagic, 35, -1, 0.75, 0.75, 0.75, 0.0);
        }
        opening.flush();
        for (int i = 0; i < count; i++) {
            if (spawn.getChunk().getEntities().length >= 50) continue;
            Zombie zombie = spawn.getWorld().spawn(spawn, Zombie.class);
            zombie.setCustomName("§d§l" + wearer.getName() + "'s Undead Minion");
            zombie.setCustomNameVisible(true);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    level > 6 ? 2 : level > 3 ? 1 : 0));
            if (level > 4) zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));
            if (level > 7) zombie.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, Integer.MAX_VALUE, 2));
            UUID summonId = zombie.getUniqueId();
            UUID wearerId = wearer.getUniqueId();
            undeadOwners.put(summonId, wearerId);
            summons.put(summonId, zombie);
            GuardianCasts.bind(summonId, wearerId, GuardianCasts.Kind.UNDEAD_RUSE,
                    () -> forgetSummon(summonId));
        }
        attacker.hidePlayer(wearer);
        Scheduling.onEntityLater(attacker, undeadInvisibilityTicks(count), () -> {
            attacker.showPlayer(wearer);
            if (witchMagic >= 0) {
                SinkReadback visible = sinks.create(env);
                visible.particle(wearer.getLocation(), witchMagic, 35, -1, 0.75, 0.75, 0.75, 0.0);
                visible.flush();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSummonDamage(EntityDamageByEntityEvent event) {
        if (spirits.containsKey(event.getDamager().getUniqueId()) && event.getEntity() instanceof Player) {
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }
        UUID ownerId = undeadOwners.get(event.getDamager().getUniqueId());
        if (ownerId != null && event.getEntity() instanceof Player player && friendly(ownerId, player)) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
        ownerId = undeadOwners.get(event.getEntity().getUniqueId());
        if (ownerId != null && event.getDamager() instanceof Player player && friendly(ownerId, player)) {
            event.setDamage(0.0);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (spirits.containsKey(event.getEntity().getUniqueId()) && event.getTarget() instanceof Player) {
            event.setTarget(null);
            event.setCancelled(true);
            return;
        }
        UUID ownerId = undeadOwners.get(event.getEntity().getUniqueId());
        if (ownerId != null && event.getTarget() instanceof Player player
                && (player.hasMetadata("spectator") || friendly(ownerId, player))) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpiritProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)
                || !(fireball.getShooter() instanceof Blaze blaze)
                || !spirits.containsKey(blaze.getUniqueId())) {
            return;
        }
        fireball.setBounce(false);
        if (relaunchingSpiritFireballs.contains(blaze.getUniqueId())) {
            spiritFireballs.add(fireball.getUniqueId());
            return;
        }
        event.setCancelled(true);
        blaze.setTarget(null);
        Vector velocity = fireball.getVelocity().clone();
        Scheduling.onEntityLater(blaze, 10L, () -> {
            if (!blaze.isValid() || !spirits.containsKey(blaze.getUniqueId())) {
                return;
            }
            relaunchingSpiritFireballs.add(blaze.getUniqueId());
            try {
                SmallFireball replacement = blaze.launchProjectile(SmallFireball.class, velocity);
                replacement.setBounce(false);
                spiritFireballs.add(replacement.getUniqueId());
            } finally {
                relaunchingSpiritFireballs.remove(blaze.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpiritExplosionPrime(ExplosionPrimeEvent event) {
        if (spiritFireballs.contains(event.getEntity().getUniqueId())) {
            event.setRadius(0.0f);
            event.setFire(false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpiritBlockIgnite(BlockIgniteEvent event) {
        Entity igniter = event.getIgnitingEntity();
        if (igniter != null && spiritFireballs.contains(igniter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpiritProjectileHit(ProjectileHitEvent event) {
        spiritFireballs.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        spiritCooldown.remove(id);
        spiritHealCooldown.remove(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        boolean tracked = spirits.containsKey(id) || undeadOwners.containsKey(id);
        if (tracked) {
            GuardianCasts.forget(id);
            event.getDrops().clear();
        }
    }

    private boolean friendly(UUID ownerId, Player other) {
        Player owner = Bukkit.getPlayer(ownerId);
        return ownerId.equals(other.getUniqueId()) || (owner != null && Allies.allied(owner, other));
    }

    private void remove(Entity summon) {
        UUID id = summon.getUniqueId();
        GuardianCasts.forget(id);
        if (summon.isValid()) summon.remove();
    }

    private void forgetSummon(UUID id) {
        spirits.remove(id);
        undeadOwners.remove(id);
        summons.remove(id);
        relaunchingSpiritFireballs.remove(id);
    }

    public void stop() {
        for (Entity summon : List.copyOf(summons.values())) {
            remove(summon);
        }
        spiritCooldown.clear();
        spiritHealCooldown.clear();
        spiritFireballs.clear();
        relaunchingSpiritFireballs.clear();
    }
}
