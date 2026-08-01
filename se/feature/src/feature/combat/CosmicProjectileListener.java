package feature.combat;

import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import engine.effect.kind.ActiveMasks;
import engine.effect.kind.ActiveSets;
import feature.soul.SoulService;
import item.view.ItemViewCache;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Cosmic's bow launch metadata, hit interception, and landing-area mechanics. One immutable shot snapshot
 * preserves the launcher levels even when the shooter swaps items before impact; the map is bounded by lazy
 * sixty-second expiry and consumed when the projectile lands.
 */
public final class CosmicProjectileListener implements Listener {

    private static final long TTL_NANOS = 60_000_000_000L;
    private static final String HIJACK = "enchants/hijack";
    private static final String DIMENSION_RIFT = "enchants/dimension-rift";
    private static final String HELLFIRE = "enchants/hellfire";

    private record Shot(UUID owner, int infernal, int venom, int explosive, int healing, int hellfire,
                        int cowification, int teleportation, int bidirectionalTeleportation, int hijack,
                        int dimensionRift, int teleblock, long stampNanos) {
        boolean active() {
            return infernal > 0 || venom > 0 || explosive > 0 || healing > 0 || hellfire > 0
                    || cowification > 0 || teleportation > 0 || bidirectionalTeleportation > 0 || hijack > 0
                    || dimensionRift > 0 || teleblock > 0;
        }
    }

    private final ItemViewCache views;
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final SoulService souls;
    private final ConcurrentHashMap<UUID, Shot> shots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> cowPassengers = new ConcurrentHashMap<>();
    private final int poison;
    private final int confusion;
    private final int wither;
    private final int healthBoost;
    private final int absorption;
    private final int flame;
    private final int lava;
    private final int explosion;
    private final int emeraldBlock;
    private final int orbPickup;
    private final int catHiss;
    private final int cowHurt;
    private final int witchMagic;
    private final int zombieMetal;
    private final int witherShoot;
    private final int ironGolem;
    private final int ironGolemDeath;
    private final int soulSand;
    private final int web;
    private final int jump;
    private final int eat;
    private final int endermanHit;
    private final int fireSound;

    public CosmicProjectileListener(ItemViewCache views, SinkFactory sinks, SinkEnv env,
                                    ProtectionService protection, SoulService souls,
                                    RegistryResolvers resolvers) {
        this.views = Objects.requireNonNull(views, "views");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.souls = Objects.requireNonNull(souls, "souls");
        Objects.requireNonNull(resolvers, "resolvers");
        poison = resolvers.potionEffect("POISON").orElse(-1);
        confusion = resolvers.potionEffect("CONFUSION").orElse(-1);
        wither = resolvers.potionEffect("WITHER").orElse(-1);
        healthBoost = resolvers.potionEffect("HEALTH_BOOST").orElse(-1);
        absorption = resolvers.potionEffect("ABSORPTION").orElse(-1);
        flame = resolvers.particle("FLAME").orElse(-1);
        lava = resolvers.particle("LAVA").orElse(-1);
        explosion = resolvers.particle("EXPLOSION_LARGE").orElse(-1);
        emeraldBlock = resolvers.material("EMERALD_BLOCK").orElse(-1);
        orbPickup = resolvers.sound("ORB_PICKUP").orElse(-1);
        catHiss = resolvers.sound("CAT_HISS").orElse(-1);
        cowHurt = resolvers.sound("COW_HURT").orElse(-1);
        witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
        zombieMetal = resolvers.sound("ZOMBIE_METAL").orElse(-1);
        witherShoot = resolvers.sound("WITHER_SHOOT").orElse(-1);
        ironGolem = resolvers.entityType("IRON_GOLEM").orElse(-1);
        ironGolemDeath = resolvers.sound("IRONGOLEM_DEATH").orElse(-1);
        soulSand = resolvers.material("SOUL_SAND").orElse(-1);
        web = resolvers.material("WEB").orElse(-1);
        jump = resolvers.potionEffect("JUMP").orElse(-1);
        eat = resolvers.sound("EAT").orElse(-1);
        endermanHit = resolvers.sound("ENDERMAN_HIT").orElse(-1);
        fireSound = resolvers.sound("FIRE").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)
                || !(event.getProjectile() instanceof Projectile projectile)
                || event.getBow() == null) {
            return;
        }
        CosmicProjectilePower.record(projectile.getUniqueId(), event.getForce());
        if (event.getForce() < 0.75F) {
            return;
        }
        Map<String, Integer> levels = views.of(event.getBow()).combat().enchants();
        boolean tierSixPlusEnabled = CosmicTierGate.tierSixPlusEnabled(shooter);
        int teleblock = tierSixPlusEnabled ? levels.getOrDefault("enchants/teleblock", 0) : 0;
        int bidirectionalTeleportation = tierSixPlusEnabled
                ? levels.getOrDefault("enchants/bidirectional-teleportation", 0) : 0;
        if (teleblock > 0) {
            int cost = teleblock * 6;
            if (!souls.costFree(shooter) && (!souls.active(shooter) || souls.carriedTotal(shooter) < cost)) {
                teleblock = 0;
            } else {
                souls.drainUpTo(shooter, cost);
                SinkReadback launch = sinks.create(env);
                if (witchMagic >= 0) {
                    launch.particle(shooter.getLocation().clone().add(0.0, 1.0, 0.0), witchMagic,
                            65, -1, 0.5, 0.5, 0.5, 0.0);
                }
                if (eat >= 0) {
                    launch.privateSound(shooter, eat, 0.4f, 0.2f);
                }
                int remaining = souls.carriedTotal(shooter);
                if (remaining % 100 == 0) {
                    launch.message(shooter, "&e&l** SOULS: &n" + remaining + "&e&l **");
                }
                launch.flush();
            }
        }
        Shot shot = new Shot(shooter.getUniqueId(),
                levels.getOrDefault("enchants/infernal", 0),
                levels.getOrDefault("enchants/venom", 0),
                levels.getOrDefault("enchants/explosive", 0),
                levels.getOrDefault("enchants/healing", 0),
                levels.getOrDefault("enchants/hellfire", 0),
                levels.getOrDefault("enchants/cowification", 0),
                levels.getOrDefault("enchants/teleportation", 0),
                bidirectionalTeleportation,
                levels.getOrDefault("enchants/hijack", 0),
                levels.getOrDefault("enchants/dimension-rift", 0),
                teleblock,
                System.nanoTime());
        if (!shot.active()) {
            return;
        }
        long now = shot.stampNanos();
        shots.values().removeIf(old -> now - old.stampNanos() > TTL_NANOS);
        cowPassengers.entrySet().removeIf(entry -> now - entry.getValue() > TTL_NANOS);
        shots.put(projectile.getUniqueId(), shot);
        if (projectile instanceof Arrow arrow) {
            if (shot.infernal() > 0) {
                arrow.setFireTicks(shot.infernal() * 60);
            }
            if (shot.hellfire() > 0) {
                arrow.setFireTicks(Integer.MAX_VALUE);
            }
        }

        // Explosive's skull has absolute passenger priority. Cowification's visible payload wins over
        // Venom's zero-XP cosmetic orb when both are present.
        if (shot.explosive() > 0) {
            Entity passenger = projectile.getWorld().spawnEntity(projectile.getLocation(),
                    org.bukkit.entity.EntityType.WITHER_SKULL);
            if (passenger instanceof WitherSkull skull) {
                skull.setIsIncendiary(false);
                skull.setYield(0.0f);
            }
            projectile.setPassenger(passenger);
        } else if (shot.cowification() > 0) {
            Entity passenger = projectile.getWorld().spawnEntity(
                    projectile.getLocation().clone().add(0.0, 3.0, 0.0), org.bukkit.entity.EntityType.COW);
            if (passenger instanceof Cow cow) {
                cow.setCanPickupItems(false);
                cow.setNoDamageTicks(200);
                cowPassengers.put(cow.getUniqueId(), now);
                Scheduling.onEntityLater(cow, 200L, () -> {
                    cowPassengers.remove(cow.getUniqueId());
                    if (cow.isValid() && !cow.isDead()) {
                        cow.remove();
                    }
                });
            }
            projectile.setPassenger(passenger);
        } else if (shot.venom() > 0) {
            Entity passenger = projectile.getWorld().spawnEntity(projectile.getLocation(),
                    org.bukkit.entity.EntityType.EXPERIENCE_ORB);
            if (passenger instanceof ExperienceOrb orb) {
                orb.setExperience(0);
            }
            projectile.setPassenger(passenger);
        }

        if ((shot.healing() > 0 && emeraldBlock >= 0) || (shot.teleportation() > 0 && witchMagic >= 0)) {
            SinkReadback sink = sinks.create(env);
            if (shot.healing() > 0 && emeraldBlock >= 0) {
                sink.blockBreakEffect(projectile.getLocation(), emeraldBlock);
            }
            if (shot.teleportation() > 0 && witchMagic >= 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                sink.particle(projectile.getLocation(), witchMagic, 35, -1,
                        random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
            }
            sink.flush();
        }
        if (shot.dimensionRift() > 0 && witchMagic >= 0) {
            SinkReadback sink = sinks.create(env);
            sink.particle(projectile.getLocation(), witchMagic, 16, -1, 0.7, 0.7, 0.7, 0.0);
            sink.flush();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onTeleblockHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        Shot shot = live(projectile.getUniqueId());
        if (shot == null || shot.teleblock() <= 0
                || ActiveSets.has(target, "sets/ranger")
                || ActiveMasks.has(target, "masks/glitch-mask")) {
            return;
        }
        int level = shot.teleblock();
        int requested = level * 3;
        int removed = removePlainPearls(target.getInventory(), requested);
        SinkReadback sink = sinks.create(env);
        sink.teleblock(target, (5 + level * 3) * 20);
        sink.message(target, "&5** TELEBLOCK [" + (5 + level * 3) + "s] [-" + removed + "ep] **");
        if (endermanHit >= 0) {
            sink.privateSound(target, endermanHit, 0.75f, 0.6f);
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHijackHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        int level = engine.effect.kind.HeldEnchantLevels.held(shooter, HIJACK);
        LivingEntity original = event.getEntity() instanceof LivingEntity living ? living : null;
        if (level <= 0 || CosmicProjectilePower.weak(projectile.getUniqueId())
                || ThreadLocalRandom.current().nextDouble() >= level * 0.08
                || original == null || original.getType() != org.bukkit.entity.EntityType.IRON_GOLEM) {
            return;
        }
        UUID formerOwnerId = engine.sink.GuardianCasts.owner(original.getUniqueId());
        if (formerOwnerId == null) {
            return;
        }
        Player formerOwner = Bukkit.getPlayer(formerOwnerId);
        if (formerOwnerId.equals(shooter.getUniqueId())
                || (formerOwner != null && CombatDispatch.friendly(shooter, formerOwner))) {
            return;
        }

        int guardianLevel = level * 2;
        double health = 50.0 + guardianLevel * 10.0;
        int potionFlags = 1
                | (guardianLevel >= 4 ? 2 : 0)
                | (guardianLevel >= 6 ? 4 : 0)
                | (guardianLevel >= 8 ? 8 : 0);
        Location spawn = original.getLocation().clone().add(0.0, 2.0, 0.0);
        String formerName = Bukkit.getOfflinePlayer(formerOwnerId).getName();
        if (formerName == null || formerName.isBlank()) {
            formerName = formerOwnerId.toString();
        }

        SinkReadback sink = sinks.create(env);
        if (ironGolem >= 0) {
            sink.guard(null, spawn, ironGolem, 1, 600, "&b&l" + shooter.getName() + "'s Guardian",
                    shooter.getUniqueId(), health, 0.0, 0, 9.0, 20, potionFlags,
                    ironGolemDeath, 1.0f, 0.55f);
        }
        if (witchMagic >= 0) {
            sink.particle(spawn.clone().add(0.0, 1.0, 0.0), witchMagic, 60, -1,
                    0.0, 0.0, 0.0, 1.2);
        }
        String announcement = "&5&l*** HIJACK (&7&m" + formerName + "&5&l -> &f"
                + shooter.getName() + "&5&l) ***";
        for (Entity nearby : original.getNearbyEntities(24.0, 26.0, 24.0)) {
            if (nearby instanceof Player audience && withinCube(spawn, audience.getLocation(), 24.0)) {
                sink.message(audience, announcement);
                if (ironGolemDeath >= 0) {
                    sink.privateSound(audience, ironGolemDeath, 0.8f, 1.2f);
                }
            }
        }
        engine.sink.GuardianCasts.forget(original.getUniqueId());
        original.remove();
        sink.flush();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onBidirectionalTeleportationHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        Shot shot = live(arrow.getUniqueId());
        if (shot == null || shot.bidirectionalTeleportation() <= 0) {
            return;
        }
        Player shooter = Bukkit.getPlayer(shot.owner());
        if (shooter == null || !shooter.canSee(target)
                || !protection.allows(shot.owner(), shooter.getLocation())
                || !protection.allows(shot.owner(), target.getLocation())) {
            return;
        }

        int level = shot.bidirectionalTeleportation();
        boolean friendly = shot.owner().equals(target.getUniqueId()) || CombatDispatch.friendly(shooter, target);
        SinkReadback sink = sinks.create(env);
        if (!friendly) {
            if (ActiveMasks.has(target, "masks/glitch-mask")) {
                return;
            }
            if (ThreadLocalRandom.current().nextDouble() <= 0.066 * level) {
                Location targetEye = target.getEyeLocation();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if (witchMagic >= 0) {
                    sink.particle(targetEye, witchMagic, 35, -1,
                            random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
                }
                if (flame >= 0) {
                    sink.particle(targetEye, flame, 10, -1,
                            random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.1);
                }

                Location shooterAt = shooter.getLocation();
                Location targetAt = target.getLocation();
                double distanceSquared = targetAt.distanceSquared(shooterAt);
                double maxDistance = level * 10.0;
                if (distanceSquared > maxDistance * maxDistance) {
                    String message = level == 5
                            ? "&c&l(!) &cYour Teleportation target is too far away to pull with Bidirectional Teleportation!"
                            : "&c&l(!) &cYour Teleportation target is too far away to pull with this Bidirectional Teleportation level!";
                    sink.message(shooter, message);
                } else {
                    org.bukkit.util.Vector vector = targetAt.toVector().subtract(shooterAt.toVector());
                    if (vector.lengthSquared() != 0.0) {
                        vector.normalize();
                    }
                    bidirectionalPull(vector, distanceSquared, level);
                    sink.setVelocity(target, vector.getX(), vector.getY(), vector.getZ());
                    if (zombieMetal >= 0) {
                        sink.privateSoundAt(target, targetAt, zombieMetal, 10.0f, 1.1f);
                        sink.privateSoundAt(shooter, targetAt, zombieMetal, 10.0f, 1.1f);
                    }
                    if (witherShoot >= 0) {
                        sink.privateSoundAt(target, targetAt, witherShoot, 10.0f, 1.5f);
                        sink.privateSoundAt(shooter, targetAt, witherShoot, 10.0f, 1.5f);
                    }
                    sink.message(target, "&c&l* BIDIRECTIONAL TELEPORT [towards: &7" + shooter.getName() + "&c&l] *");
                    sink.message(shooter, "&d&l* BIDIRECTIONAL TELEPORT [pulling: &7" + target.getName() + "&d&l] *");
                }
                event.setCancelled(true);
                event.setDamage(0.0);
                sink.flush();
                return;
            }

            if (!(target.getWalkSpeed() < 0.2f)) {
                // The source truncates every max-level duration to one second despite advertising 1–2s.
                // Round its authored 1 + 0.125L curve so the intended second tier becomes reachable.
                int seconds = (int) Math.round(1.0 + level * 0.125);
                sink.movementSpeed(target, 0.0, seconds * 20);
                sink.message(target, "&c&l* BIDIRECTIONAL TRAPPED [by: &7" + shooter.getName()
                        + " (" + seconds + "s)]&c&l *");
                sink.flush();
            }
            return;
        }

        if ("cosmic-station-1".equals(target.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0);
        arrow.remove();

        Location origin = shooter.getLocation();
        Location destination = target.getLocation();
        destination.setPitch(origin.getPitch());
        destination.setYaw(origin.getYaw());
        if (origin.getWorld() != destination.getWorld() || origin.distanceSquared(destination) > 900.0) {
            sink.message(shooter, "&c&l(!) &cYour ally is too far away to teleport to with this level of Bidirectional Teleportation.");
            sink.flush();
            return;
        }
        boolean restrictedWorld = destination.getWorld().getEnvironment() == World.Environment.THE_END
                || "world_koth".equals(destination.getWorld().getName());
        if (restrictedWorld && protection.allows(shot.owner(), origin)
                && !protection.allows(shot.owner(), destination)) {
            sink.message(shooter, "&c&l(!) &cYou cannot teleport from PvP-enabled to PvP-disabled with Bidirectional Teleportation in The End or KOTH.");
            sink.flush();
            return;
        }
        sink.teleport(shooter, destination);
        if (orbPickup >= 0) {
            sink.privateSoundAt(shooter, destination, orbPickup, 0.75f, 0.341f);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location cue = target.getLocation().clone().add(0.0, 0.5, 0.0);
        if (witchMagic >= 0) {
            sink.particle(cue, witchMagic, 35, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }
        if (flame >= 0) {
            sink.particle(cue, flame, 10, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.1);
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onCowDamage(EntityDamageEvent event) {
        Long stamp = cowPassengers.get(event.getEntity().getUniqueId());
        if (stamp == null || System.nanoTime() - stamp > TTL_NANOS) {
            if (stamp != null) {
                cowPassengers.remove(event.getEntity().getUniqueId(), stamp);
            }
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker) {
            cowPassengers.remove(event.getEntity().getUniqueId());
            SinkReadback sink = sinks.create(env);
            if (cowHurt >= 0) {
                sink.privateSoundAt(attacker, event.getEntity().getLocation(), cowHurt, 1.0f, 0.7f);
            }
            event.getEntity().remove();
            sink.flush();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHealingHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        Shot shot = live(projectile.getUniqueId());
        if (shot == null || shot.healing() <= 0) {
            return;
        }
        Player shooter = Bukkit.getPlayer(shot.owner());
        if (shooter == null) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        if (shot.owner().equals(target.getUniqueId())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            if (catHiss >= 0) {
                sink.privateSound(shooter, catHiss, 0.85f, 0.2f);
            }
            sink.flush();
            return;
        }
        if (!CombatDispatch.friendly(shooter, target)) {
            return;
        }

        int level = shot.healing();
        int heal = ThreadLocalRandom.current().nextInt(level, level * 3);
        boolean absorptionProc = level >= 3
                && ThreadLocalRandom.current().nextDouble() < 0.15 * (level - 2);
        event.setCancelled(true);
        event.setDamage(0.0);
        projectile.remove();

        if (target.getHealth() + heal > target.getMaxHealth()) {
            sink.setHealth(target, target.getMaxHealth());
            if (!absorptionProc && healthBoost >= 0) {
                sink.potion(target, healthBoost, level - 1, 20 * (4 + level));
            }
        } else {
            sink.heal(target, heal);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.25 * level) {
            sink.repairMostDamagedArmor(target, 1);
        }
        if (absorptionProc && absorption >= 0) {
            sink.potion(target, absorption, level - 3, 20 * (1 + level));
        }
        if (orbPickup >= 0) {
            sink.privateSound(shooter, orbPickup, 0.75f, 0.341f);
        }
        if (emeraldBlock >= 0) {
            sink.blockBreakEffect(target, emeraldBlock, "feet", 0.5);
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onTeleportationHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        Shot shot = live(projectile.getUniqueId());
        if (shot == null || shot.teleportation() <= 0) {
            return;
        }
        Player shooter = Bukkit.getPlayer(shot.owner());
        if (shooter == null) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        if (shot.owner().equals(target.getUniqueId())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            if (catHiss >= 0) {
                sink.privateSound(shooter, catHiss, 0.85f, 0.2f);
            }
            sink.flush();
            return;
        }
        if (!CombatDispatch.friendly(shooter, target)
                || "cosmic-station-1".equals(shooter.getWorld().getName())) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0);
        projectile.remove();

        Location origin = shooter.getLocation();
        Location destination = target.getLocation();
        destination.setPitch(origin.getPitch());
        destination.setYaw(origin.getYaw());
        double maxDistance = shot.teleportation() * 6.0;
        if (origin.getWorld() != destination.getWorld()
                || origin.distanceSquared(destination) > maxDistance * maxDistance) {
            sink.message(shooter, "&c&l(!) &cYour ally is too far away to teleport to with this level of Teleporation.");
            sink.flush();
            return;
        }

        boolean restrictedWorld = origin.getWorld().getEnvironment() == World.Environment.THE_END
                || "world_koth".equals(origin.getWorld().getName());
        if (restrictedWorld && protection.allows(shot.owner(), origin)
                && !protection.allows(shot.owner(), destination)) {
            sink.message(shooter, "&c&l(!) &cYou cannot teleport from PvP-enabled to PvP-disabled with Teleportation in The End or KOTH.");
            sink.flush();
            return;
        }

        sink.teleport(shooter, destination);
        if (orbPickup >= 0) {
            sink.privateSoundAt(shooter, destination, orbPickup, 0.75f, 0.341f);
        }
        if (witchMagic >= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location cue = target.getLocation().clone().add(0.0, 0.5, 0.0);
            sink.particle(cue, witchMagic, 35, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHellfireDirectHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = engine.effect.kind.HeldEnchantLevels.held(shooter, HELLFIRE);
        if (level <= 0 || CosmicProjectilePower.weak(projectile.getUniqueId())) {
            return;
        }
        LivingEntity routedTarget = target;
        if (target instanceof Player playerTarget) {
            CosmicReflect.Route route = CosmicReflect.route(shooter, playerTarget, level, 4);
            routedTarget = route.target();
            if (!route.source().canSee(route.target())
                    || route.source().getGameMode() != org.bukkit.GameMode.SURVIVAL) {
                return;
            }
        }
        routedTarget.setFireTicks(level * 40);
        SinkReadback sink = sinks.create(env);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (flame >= 0) {
            sink.particle(routedTarget.getEyeLocation(), flame, 30, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.15);
        }
        if (lava >= 0) {
            sink.particle(routedTarget.getEyeLocation(), lava, 20, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }
        sink.flush();
    }

    @EventHandler
    public void onImpact(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Shot shot = shots.remove(projectile.getUniqueId());
        if (shot == null || System.nanoTime() - shot.stampNanos() > TTL_NANOS) {
            return;
        }
        Entity passenger = projectile.getPassenger();
        if (passenger != null) {
            cowPassengers.remove(passenger.getUniqueId());
            passenger.remove();
        }
        Location impact = projectile.getLocation();
        if (!protection.allows(shot.owner(), impact)) {
            return;
        }

        SinkReadback sink = sinks.create(env);
        Player owner = Bukkit.getPlayer(shot.owner());
        int hellfireRadius = hellfireRadius(shot.hellfire(), impact.getWorld().getName());
        int radius = Math.max(Math.max(shot.infernal(), shot.venom()),
                Math.max(Math.max(shot.explosive(), shot.cowification()), hellfireRadius));
        if (radius > 0) {
            for (Entity entity : projectile.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof LivingEntity living)
                        || shot.owner().equals(entity.getUniqueId())
                        || (entity instanceof Player player && owner != null
                            && CombatDispatch.friendly(owner, player))) {
                    continue;
                }
                boolean notSpectator = !entity.hasMetadata("spectator");
                boolean ordinaryTarget = notSpectator && !entity.hasMetadata("do_not_clear");
                if (shot.infernal() > 0 && ordinaryTarget
                        && withinCube(impact, entity.getLocation(), shot.infernal())) {
                    sink.ignite(living, shot.infernal() * 20);
                }
                // Intended correction of VenomListener's impossible projectile-is-Player predicate:
                // its final source predicate still deliberately limits the area poison to players.
                if (shot.venom() > 0 && notSpectator && entity instanceof Player && poison >= 0
                        && withinCube(impact, entity.getLocation(), shot.venom())) {
                    sink.potion(living, poison, 1, shot.venom() * 25);
                }
                if (shot.explosive() > 0 && notSpectator && !(entity instanceof EnderDragon) && wither >= 0
                        && withinCube(impact, entity.getLocation(), shot.explosive())) {
                    sink.potion(living, wither, 1, shot.explosive() * 20);
                }
                if (shot.cowification() > 0 && confusion >= 0
                        && withinCube(impact, entity.getLocation(), shot.cowification())) {
                    sink.potion(living, confusion, 1, shot.cowification() * 25);
                }
                if (shot.hellfire() > 0 && ordinaryTarget && entity instanceof Player
                        && withinCube(impact, entity.getLocation(), hellfireRadius)
                        && protection.allows(shot.owner(), entity.getLocation())) {
                    living.setFireTicks(living.getFireTicks() + shot.hellfire() * 20);
                    if (lava >= 0) {
                        sink.particle(entity.getLocation().clone().add(0.0, 1.0, 0.0), lava, 16, -1,
                                randomSpread(), randomSpread(), randomSpread(), 0.5);
                    }
                    double delayedDamage = hellfireDelayedDamage(shot.hellfire(), impact.getWorld().getName());
                    Scheduling.onEntityLater(living, 1L, () -> {
                        if (!living.isDead()) {
                            living.damage(delayedDamage);
                        }
                    });
                }
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (shot.infernal() > 0 && flame >= 0) {
            sink.particle(impact, flame, 20, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }
        if (shot.explosive() > 0 && explosion >= 0) {
            sink.particle(impact, explosion, 8, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }
        if (shot.cowification() > 0) {
            if (cowHurt >= 0) {
                sink.sound(impact, cowHurt, 1.0f, 0.85f);
            }
            if (explosion >= 0) {
                sink.particle(impact, explosion, 8, -1,
                        random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
            }
        }
        if (shot.hellfire() > 0) {
            if (fireSound >= 0) {
                sink.sound(impact, fireSound, 1.0f, 0.85f);
            }
            if (explosion >= 0) {
                sink.particle(impact, explosion, 8, -1,
                        random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
            }
            if (flame >= 0) {
                sink.particle(impact, flame, 45, -1,
                        random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.25);
            }
        }
        sink.flush();
        if (shot.hellfire() > 0) {
            projectile.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDimensionRiftHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        int level = engine.effect.kind.HeldEnchantLevels.held(shooter, DIMENSION_RIFT);
        if (level <= 0 || level > 4 || CosmicProjectilePower.weak(projectile.getUniqueId())) {
            return;
        }
        CosmicReflect.Route route = CosmicReflect.route(shooter, target, level, 4);
        shooter = route.source();
        target = route.target();
        if (ThreadLocalRandom.current().nextDouble() > level * 0.05
                || target.getWorld().getName().equals("world_koth")
                || shooter.getWorld().getName().startsWith("dungeon")
                || soulSand < 0 || web < 0
                || !protection.allows(shooter.getUniqueId(), target.getLocation())) {
            return;
        }

        int minX = level >= 4 ? -1 : 0;
        int minZ = level >= 3 ? -1 : 0;
        int duration = level * 15 + 40;
        Location base = target.getLocation().subtract(0.0, 1.0, 0.0);
        java.util.ArrayList<Location> changed = new java.util.ArrayList<>();
        SinkReadback sink = sinks.create(env);
        for (int dx = minX; dx <= 1; dx++) {
            for (int dz = minZ; dz <= 1; dz++) {
                Location floor = base.clone().add(dx, 0.0, dz);
                if (!riftReplaceable(floor)) {
                    continue;
                }
                sink.tempBlock(floor, soulSand, duration, 2, true);
                changed.add(floor.clone());
                if (ThreadLocalRandom.current().nextDouble() < level * 0.1) {
                    Location top = floor.clone().add(0.0, 1.0, 0.0);
                    if (riftReplaceable(top)) {
                        sink.tempBlock(top, web, duration, 2, true);
                        changed.add(top.clone());
                    }
                }
            }
        }
        sink.flush();
        if (changed.isEmpty()) {
            return;
        }
        Scheduling.onRegionLater(base, duration, () -> {
            java.util.HashSet<UUID> lifted = new java.util.HashSet<>();
            SinkReadback ending = sinks.create(env);
            for (Location location : changed) {
                for (Entity nearby : location.getWorld().getNearbyEntities(location, 2.0, 2.0, 2.0)) {
                    if (nearby instanceof Player player && lifted.add(player.getUniqueId())
                            && location.distanceSquared(player.getLocation()) <= 4.0) {
                        if (jump >= 0) {
                            ending.removePotion(player, jump);
                        }
                        ending.setVelocity(player, 0.0, 0.5, 0.0);
                    }
                }
            }
            ending.flush();
        });
    }

    private static boolean riftReplaceable(Location location) {
        org.bukkit.block.Block block = location.getBlock();
        org.bukkit.Material type = block.getType();
        String name = type.name();
        return !(block.getState() instanceof Chest)
                && type != org.bukkit.Material.SOUL_SAND
                && !name.equals("WEB") && !name.equals("COBWEB")
                && type != org.bukkit.Material.BEDROCK
                && !name.equals("ENDER_PORTAL") && !name.equals("END_PORTAL")
                && !name.equals("PORTAL") && !name.equals("NETHER_PORTAL");
    }

    private static boolean withinCube(Location center, Location point, double radius) {
        return center.getWorld() == point.getWorld()
                && Math.abs(center.getX() - point.getX()) <= radius
                && Math.abs(center.getY() - point.getY()) <= radius
                && Math.abs(center.getZ() - point.getZ()) <= radius;
    }

    static int hellfireRadius(int level, String worldName) {
        int radius = level * 2;
        return "world_duels2".equals(worldName) ? radius / 2 : radius;
    }

    static double hellfireDelayedDamage(int level, String worldName) {
        double damage = 1.0 + level;
        return "world_duels2".equals(worldName) ? damage / 1.5 : damage;
    }

    static org.bukkit.util.Vector bidirectionalPull(org.bukkit.util.Vector vector,
                                                    double distanceSquared, int level) {
        double pull = Math.min(Math.max(1.0, distanceSquared / 50.0), 6.0 + level * 0.5);
        double normalizedY = vector.getY();
        vector.multiply(-pull);
        vector.setY(normalizedY * (-pull / 1.75));
        if (vector.getY() < 0.01 && vector.getY() > -0.01) {
            vector.multiply(new org.bukkit.util.Vector(1.0, 7.5, 1.0));
        }
        return vector;
    }

    private static double randomSpread() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private static int removePlainPearls(PlayerInventory inventory, int requested) {
        int remaining = requested;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != org.bukkit.Material.ENDER_PEARL
                    || (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName())) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, stack);
            }
        }
        return requested - remaining;
    }

    private Shot live(UUID projectile) {
        Shot shot = shots.get(projectile);
        if (shot != null && System.nanoTime() - shot.stampNanos() <= TTL_NANOS) {
            return shot;
        }
        if (shot != null) {
            shots.remove(projectile, shot);
        }
        return null;
    }
}
