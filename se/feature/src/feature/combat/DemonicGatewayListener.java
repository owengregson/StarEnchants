package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.util.Vector;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/** Native Necromancer-set Demonic Gateway implementation. */
public final class DemonicGatewayListener implements Listener {

    private static final String ENCHANT = "enchants/demonic-gateway";
    private static final long OWNER_COOLDOWN = 200L;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final Map<UUID, Long> ownerCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Crystal> crystals = new ConcurrentHashMap<>();
    private final Map<UUID, GatewayShot> shots = new ConcurrentHashMap<>();
    private final int ghastFireball;
    private final int witherShoot;
    private final int flames;
    private final int witchMagic;
    private final int largeExplosion;

    public DemonicGatewayListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                                  RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(resolvers, "resolvers");
        ghastFireball = resolvers.sound("GHAST_FIREBALL").orElse(-1);
        witherShoot = resolvers.sound("WITHER_SHOOT").orElse(-1);
        flames = resolvers.particle("FLAME").orElse(-1);
        witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
        largeExplosion = resolvers.particle("EXPLOSION_LARGE").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        Player attacker = MarkOfTheBeastListener.resolvePlayerForCosmic(event.getDamager());
        if (!(event.getEntity() instanceof Player wearer) || attacker == null
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(wearer, env)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 6
                || ThreadLocalRandom.current().nextDouble() > 0.015 + 0.005 * level) {
            return;
        }
        long now = env.nowTicks().getAsLong();
        Long until = ownerCooldown.get(wearer.getUniqueId());
        if (until != null && until > now) {
            return;
        }

        int count = Math.min(2 + level / 2, 5);
        int radius = 3 + 6 / 2 + count / 2; // source integer arithmetic with max=6.
        int duration = (int) (Math.min(level * 2.5, 20.0) * 20.0);
        Set<EnderCrystal> spawned = ConcurrentHashMap.newKeySet();
        Location center = wearer.getLocation();
        for (int index = 0; index < count; index++) {
            double angle = Math.toRadians(index * (360.0 / count));
            Location requested = center.clone().add(radius * Math.cos(angle), 0.0, radius * Math.sin(angle));
            Location ground = openGround(requested, 4);
            if (ground == null || !protection.allows(wearer.getUniqueId(), ground)) {
                continue;
            }
            Entity entity = ground.getWorld().spawnEntity(ground.clone().add(0.0, 1.0, 0.0),
                    org.bukkit.entity.EntityType.ENDER_CRYSTAL);
            if (!(entity instanceof EnderCrystal crystal)) {
                entity.remove();
                continue;
            }
            spawned.add(crystal);
            Crystal state = new Crystal(wearer.getUniqueId(), level, duration, crystal);
            crystals.put(crystal.getUniqueId(), state);
            cueSpawn(wearer, crystal);
            state.shooter = Scheduling.repeatingEntity(crystal, 30L, 3L,
                    () -> tickCrystal(crystal, state));
            Scheduling.onEntityLater(crystal, duration, () -> removeCrystal(crystal, state));
        }
        if (spawned.isEmpty()) {
            return;
        }
        ownerCooldown.put(wearer.getUniqueId(), now + OWNER_COOLDOWN);
        SinkReadback announce = sinks.create(env);
        for (Entity nearby : wearer.getNearbyEntities(24.0, 24.0, 24.0)) {
            if (nearby instanceof Player player && !CombatDispatch.friendly(wearer, player)) {
                announce.message(player, "&2&l** DEMONIC GATEWAY " + roman(level) + " (&a"
                        + wearer.getName() + "&2&l) **");
            }
        }
        announce.flush();
    }

    private void cueSpawn(Player owner, EnderCrystal crystal) {
        SinkReadback sink = sinks.create(env);
        if (ghastFireball >= 0) {
            sink.sound(crystal.getLocation(), ghastFireball, 3.0f, 0.9f);
        }
        if (flames >= 0) {
            sink.particle(crystal.getLocation(), flames, 1, -1, 0.0, 0.0, 0.0, 0.0);
        }
        sink.flush();
        crystal.getWorld().strikeLightningEffect(crystal.getLocation());
    }

    private void tickCrystal(EnderCrystal crystal, Crystal state) {
        if (!crystal.isValid() || crystal.isDead()) {
            removeCrystal(crystal, state);
            return;
        }
        if (state.cooldown > 0) {
            state.cooldown--;
            return;
        }
        Player owner = org.bukkit.Bukkit.getPlayer(state.owner);
        if (owner == null || !owner.isOnline()) {
            removeCrystal(crystal, state);
            return;
        }
        double range = 5 + state.level;
        for (Entity nearby : crystal.getNearbyEntities(range, range, range)) {
            if (!(nearby instanceof Player target) || target.equals(owner)
                    || target.getGameMode() != GameMode.SURVIVAL
                    || target.hasMetadata("spectator") || target.hasMetadata("NPC")
                    || !owner.canSee(target) || CombatDispatch.friendly(owner, target)
                    || !protection.allows(owner.getUniqueId(), target.getLocation())) {
                continue;
            }
            state.cooldown = 8 + ThreadLocalRandom.current().nextInt(6);
            shoot(crystal, owner, target, state.level);
            return;
        }
    }

    private void shoot(EnderCrystal crystal, Player owner, Player target, int level) {
        Location origin = crystal.getLocation().add(0.0, 1.0, 0.0);
        Location targetAt = target.getLocation();
        Vector velocity = new Vector(targetAt.getX() - origin.getX(),
                targetAt.getY() + target.getEyeHeight() - 1.1 - origin.getY(),
                targetAt.getZ() - origin.getZ());
        velocity.multiply(0.05 + level * 0.0025);
        Entity entity = origin.getWorld().spawnEntity(origin, org.bukkit.entity.EntityType.WITHER_SKULL);
        if (!(entity instanceof WitherSkull skull)) {
            entity.remove();
            return;
        }
        skull.setShooter(owner);
        skull.setVelocity(velocity);
        skull.setIsIncendiary(false);
        skull.setYield(0.0f);
        shots.put(skull.getUniqueId(), new GatewayShot(owner.getUniqueId(), owner.getName(), level,
                crystal, skull));
        Scheduling.onEntityLater(skull, 100L, () -> {
            shots.remove(skull.getUniqueId());
            if (skull.isValid()) {
                skull.remove();
            }
        });
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSkullHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof WitherSkull skull)
                || !(event.getEntity() instanceof Player target)) {
            return;
        }
        GatewayShot shot = shots.remove(skull.getUniqueId());
        if (shot == null) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0);
        skull.remove();
        Player owner = org.bukkit.Bukkit.getPlayer(shot.owner);
        if (owner == null || target.isDead() || target.hasMetadata("spectator")
                || target.getGameMode() != GameMode.SURVIVAL
                || CombatDispatch.friendly(owner, target)
                || !protection.allows(shot.owner, target.getLocation())) {
            return;
        }
        double amount = target.getMaxHealth() * 0.05;
        SinkReadback sink = sinks.create(env);
        sink.hurtAnimation(target);
        sink.setHealth(target, Math.max(0.0, target.getHealth() - amount));

        boolean trapped = ThreadLocalRandom.current().nextDouble() <= 0.06 + shot.level * 0.03;
        if (trapped && !(target.getWalkSpeed() < 0.2f)) {
            int seconds = (int) Math.round(Math.min(5.0, 1.0 + shot.level * 0.125));
            sink.movementSpeed(target, 0.0, seconds * 20);
            sink.message(target, "&c&l* DEMONIC GATEWAY TRAP [by: &7" + shot.ownerName + " ("
                    + seconds + "s)&c&l] *");
        } else if (!trapped && target.getWalkSpeed() > 0.0f) {
            Crystal source = crystals.get(shot.crystal.getUniqueId());
            Entity crystal = source == null || !shot.crystal.isValid() ? null : shot.crystal;
            if (crystal != null && crystal.getWorld() == target.getWorld()
                    && crystal.getLocation().distanceSquared(target.getLocation()) < 16.0) {
                Vector push = target.getLocation().toVector().subtract(crystal.getLocation().toVector());
                if (push.lengthSquared() != 0.0) {
                    push.normalize();
                }
                push.multiply(1.1);
                sink.setVelocity(target, push.getX(), push.getY(), push.getZ());
                if (witherShoot >= 0) {
                    sink.privateSound(target, witherShoot, 1.0f, 1.1f);
                }
            }
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof EnderCrystal
                && crystals.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler
    public void onPrime(ExplosionPrimeEvent event) {
        if (crystals.containsKey(event.getEntity().getUniqueId())
                || shots.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            explosion(event.getEntity().getLocation());
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (crystals.containsKey(event.getEntity().getUniqueId())
                || shots.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            event.blockList().clear();
            explosion(event.getLocation());
        }
    }

    private void explosion(Location at) {
        if (largeExplosion >= 0) {
            SinkReadback sink = sinks.create(env);
            sink.particle(at, largeExplosion, 10, -1, 0.5, 0.5, 0.5, 0.1);
            sink.flush();
        }
    }

    public void stop() {
        for (Crystal crystal : java.util.List.copyOf(crystals.values())) {
            removeCrystal(crystal.entity, crystal);
        }
        for (GatewayShot shot : java.util.List.copyOf(shots.values())) {
            if (shot.projectile().isValid()) {
                shot.projectile().remove();
            }
        }
        shots.clear();
        ownerCooldown.clear();
    }

    private void removeCrystal(EnderCrystal crystal, Crystal expected) {
        if (!crystals.remove(crystal.getUniqueId(), expected)) {
            return;
        }
        if (expected.shooter != null) {
            expected.shooter.cancel();
        }
        if (witchMagic >= 0 && crystal.isValid()) {
            SinkReadback sink = sinks.create(env);
            sink.particle(crystal.getLocation(), witchMagic, 16, -1, 0.75, 0.75, 0.75, 0.0);
            sink.flush();
        }
        crystal.remove();
    }

    private static Location openGround(Location start, int vertical) {
        for (int dy = -vertical; dy < vertical; dy++) {
            Location location = start.clone().add(0.0, dy, 0.0);
            if (location.getY() <= 0.0) {
                continue;
            }
            Block ground = location.getBlock();
            if (ground.getType().isSolid()
                    && spawnable(ground.getRelative(BlockFace.UP).getType())
                    && spawnable(ground.getRelative(BlockFace.UP, 2).getType())) {
                return location;
            }
        }
        return null;
    }

    private static boolean spawnable(Material material) {
        String name = material.name();
        return material == Material.AIR || name.equals("WATER") || name.equals("STATIONARY_WATER")
                || name.equals("LAVA") || name.equals("STATIONARY_LAVA");
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> Integer.toString(level);
        };
    }

    private static final class Crystal {
        final UUID owner;
        final int level;
        final int duration;
        final EnderCrystal entity;
        volatile int cooldown;
        volatile TaskHandle shooter;

        Crystal(UUID owner, int level, int duration, EnderCrystal entity) {
            this.owner = owner;
            this.level = level;
            this.duration = duration;
            this.entity = entity;
        }
    }

    private record GatewayShot(UUID owner, String ownerName, int level, EnderCrystal crystal,
                               WitherSkull projectile) {
    }
}
