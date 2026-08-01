package feature.combat;

import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.sink.GuardianCasts;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.compat.Hands;
import item.view.ItemViewCache;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended server-side implementation of Cosmic's Necromancer mastery Rot and Decay. */
public final class RotAndDecayListener implements Listener {

    private static final String ENCHANT = "enchants/rot-and-decay";
    private static final String YIJKI = "sets/mother-of-yijki";

    private record Stack(int count, long expiresAt) {
    }

    private record Decay(long token, UUID owner, int level, long expiresAt, Set<Block> floor) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final ItemViewCache views;
    private final Hands hands;
    private final LongSupplier nowTicks;
    private final int zombieMetal;
    private final int largeSmoke;
    private final int witchMagic;
    private final Map<Long, Decay> decays = new ConcurrentHashMap<>();
    private final Map<UUID, Stack> stacks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> corpseOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Zombie> corpses = new ConcurrentHashMap<>();
    private final AtomicLong tokens = new AtomicLong();

    public RotAndDecayListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                               ItemViewCache views, Hands hands, RegistryResolvers resolvers,
                               LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.views = Objects.requireNonNull(views, "views");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.zombieMetal = resolvers.sound("ZOMBIE_METAL").orElse(-1);
        this.largeSmoke = resolvers.particle("SMOKE_LARGE").orElse(-1);
        this.witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || !CosmicTierGate.tierSixPlusEnabled(wearer)) {
            return;
        }
        Player attacker = attackingPlayer(event);
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (attacker == null || level <= 0 || CombatDispatch.friendly(wearer, attacker)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())
                || !protection.allows(attacker.getUniqueId(), wearer.getLocation())
                || ThreadLocalRandom.current().nextDouble() > 0.05 + level * 0.01) {
            return;
        }
        activate(wearer, level);
    }

    private void activate(Player owner, int level) {
        int radius = 3 + level / 3;
        Set<Block> floor = findFloor(owner, radius);
        long now = nowTicks.getAsLong();
        long token = tokens.incrementAndGet();
        Decay decay = new Decay(token, owner.getUniqueId(), level, now + level * 20L, floor);
        decays.put(token, decay);

        showFloor(owner, floor);
        clearEnemySummons(owner);
        spawnCorpses(owner, 1 + level / 3, level);
        Scheduling.onEntityLater(owner, 20L, () -> tick(owner, decay));
    }

    private Set<Block> findFloor(Player owner, int radius) {
        Block center = owner.getLocation().getBlock();
        int radiusSquared = radius * radius;
        Set<Block> floor = new HashSet<>();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();
                    String name = type.name();
                    Material above = block.getRelative(0, 1, 0).getType();
                    if (block.getLocation().distanceSquared(center.getLocation()) <= radiusSquared
                            && type.isSolid() && !name.equals("STEP") && !name.equals("WOOD_STEP")
                            && !name.endsWith("_SLAB")
                            && (above == Material.AIR || above.name().contains("WATER") || above.name().contains("LAVA"))
                            && protection.allows(owner.getUniqueId(), block.getLocation())) {
                        floor.add(block);
                    }
                }
            }
        }
        return floor;
    }

    @SuppressWarnings("deprecation")
    private void showFloor(Player owner, Set<Block> floor) {
        Material hostileFloor = material("END_STONE", "ENDER_STONE");
        for (Player viewer : owner.getWorld().getPlayers()) {
            Material material = viewer.equals(owner) || CombatDispatch.friendly(owner, viewer)
                    ? Material.GLOWSTONE : hostileFloor;
            Scheduling.onEntity(viewer, () -> {
                for (Block block : floor) {
                    viewer.sendBlockChange(block.getLocation(), material, (byte) 0);
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private void restoreFloor(Player owner, Set<Block> floor) {
        for (Player viewer : owner.getWorld().getPlayers()) {
            Scheduling.onEntity(viewer, () -> {
                for (Block block : floor) {
                    viewer.sendBlockChange(block.getLocation(), block.getType(), block.getData());
                }
            });
        }
    }

    private void tick(Player owner, Decay decay) {
        Decay live = decays.get(decay.token());
        if (live != decay || !owner.isOnline()) {
            return;
        }
        long now = nowTicks.getAsLong();
        if (now >= decay.expiresAt()) {
            decays.remove(decay.token(), decay);
            restoreFloor(owner, decay.floor());
            return;
        }

        for (Player target : owner.getWorld().getPlayers()) {
            if (target.equals(owner) || target.getGameMode() != GameMode.SURVIVAL
                    || CombatDispatch.friendly(owner, target)
                    || !protection.allows(owner.getUniqueId(), target.getLocation())
                    || yijkiWeaponImmune(target)) {
                continue;
            }
            Block feet = target.getLocation().getBlock();
            if (!decay.floor().contains(feet) && !decay.floor().contains(feet.getRelative(0, -1, 0))) {
                continue;
            }
            applyDecay(target, decay.level(), now);
        }
        Scheduling.onEntityLater(owner, 10L, () -> tick(owner, decay));
    }

    @SuppressWarnings("deprecation")
    private void applyDecay(Player target, int level, long now) {
        Stack current = stacks.get(target.getUniqueId());
        int old = current == null || current.expiresAt() <= now ? 0 : current.count();
        int count = Math.min(Math.min(6, level), old + 1);
        stacks.put(target.getUniqueId(), new Stack(count, now + 60L));
        double damage = decayDamage(count);
        SinkReadback sink = sinks.create(env);
        sink.damage(target, damage);
        sink.message(target, "&c&l* DECAYING [&7-" + number(damage)
                + "HP (" + count + " stacks)&c&l] *");
        sink.flush();
    }

    static double decayDamage(int stacks) {
        return stacks * 2.0;
    }

    private void clearEnemySummons(Player owner) {
        for (Entity entity : owner.getNearbyEntities(15.0, 15.0, 15.0)) {
            UUID entityId = entity.getUniqueId();
            if (entity instanceof Player || !GuardianCasts.rotAndDecayPurgeable(entityId)) {
                continue;
            }
            UUID summonOwner = GuardianCasts.owner(entityId);
            Player summoner = summonOwner == null ? null : owner.getServer().getPlayer(summonOwner);
            boolean purge = summoner == null
                    || !summonOwner.equals(owner.getUniqueId())
                    || CombatDispatch.friendly(owner, summoner);
            if (!purge) {
                continue;
            }
            GuardianCasts.forget(entityId);
            SinkReadback sink = sinks.create(env);
            if (largeSmoke >= 0) {
                sink.particle(entity.getLocation().clone().add(0.0, 1.0, 0.0), largeSmoke,
                        10, -1, 0.3, 0.3, 0.3, 0.01);
            }
            if (witchMagic >= 0) {
                sink.particle(entity.getLocation().clone().add(0.0, 1.0, 0.0), witchMagic,
                        12, -1, 0.7, 0.7, 0.7, 0.0);
            }
            sink.flush();
            entity.remove();
        }
    }

    private void spawnCorpses(Player owner, int count, int level) {
        Location at = owner.getLocation().clone().add(0.0, 1.0, 0.0);
        for (int i = 0; i < count && at.getChunk().getEntities().length < 50; i++) {
            Zombie zombie = at.getWorld().spawn(at, Zombie.class);
            zombie.setBaby(false);
            zombie.setCanPickupItems(false);
            zombie.setCustomName("§2§l" + owner.getName() + "'s Rotting Corpse");
            zombie.setCustomNameVisible(true);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE,
                    level > 6 ? 2 : level > 3 ? 1 : 0));
            Vector velocity = new Vector(signedHalf(), ThreadLocalRandom.current().nextDouble() / 10.0, signedHalf());
            if (velocity.lengthSquared() > 0.0) {
                velocity.normalize();
            }
            zombie.setVelocity(velocity);
            UUID corpseId = zombie.getUniqueId();
            UUID ownerId = owner.getUniqueId();
            corpseOwners.put(corpseId, ownerId);
            corpses.put(corpseId, zombie);
            GuardianCasts.bind(corpseId, ownerId, GuardianCasts.Kind.UNDEAD_RUSE,
                    () -> forgetCorpse(corpseId));
        }
    }

    private void forgetCorpse(UUID id) {
        corpseOwners.remove(id);
        corpses.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onCorpseHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Zombie corpse)) {
            return;
        }
        UUID ownerId = corpseOwners.remove(corpse.getUniqueId());
        if (ownerId == null) {
            return;
        }
        corpses.remove(corpse.getUniqueId());
        GuardianCasts.forget(corpse.getUniqueId());
        event.setCancelled(true);
        event.setDamage(0.0);
        corpse.remove();

        SinkReadback sink = sinks.create(env);
        if (largeSmoke >= 0) {
            sink.particle(corpse.getLocation().clone().add(0.0, 0.75, 0.0), largeSmoke,
                    20, -1, 0.5, 0.5, 0.5, 0.01);
        }
        if (yijkiWeaponImmune(victim)) {
            sink.flush();
            return;
        }
        sink.hurtAnimation(victim);
        Player owner = victim.getServer().getPlayer(ownerId);
        String ownerName = owner == null ? "Unknown" : owner.getName();
        if (ThreadLocalRandom.current().nextDouble() <= 0.5) {
            double damage = victim.getMaxHealth() * 0.1;
            sink.setHealth(victim, Math.max(0.0, victim.getHealth() - damage));
            sink.message(victim, "&c&l* ROTTED [&7-" + number(damage) + "HP (" + ownerName + ")&c&l] *");
        } else {
            ItemStack[] armor = victim.getInventory().getArmorContents();
            for (int slot = 0; slot < armor.length; slot++) {
                ItemStack piece = armor[slot];
                if (piece == null || piece.getType() == Material.AIR || piece.getType().getMaxDurability() <= 0) {
                    continue;
                }
                int damage = (int) Math.ceil(piece.getType().getMaxDurability() * 0.025);
                sink.damageArmorSlot(victim, slot, damage);
                sink.message(victim, "&c&l* DECAYED [&7- " + damage + " Durability&c&l] *");
                if (zombieMetal >= 0) {
                    sink.sound(victim, zombieMetal, 3.0f, 1.5f);
                }
                break;
            }
        }
        sink.flush();
    }

    @EventHandler
    public void onCorpseDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (corpseOwners.remove(id) != null) {
            event.getDrops().clear();
            corpses.remove(id);
            GuardianCasts.forget(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stacks.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        for (Decay decay : new ArrayList<>(decays.values())) {
            Player owner = org.bukkit.Bukkit.getPlayer(decay.owner());
            if (owner != null) {
                restoreFloor(owner, decay.floor());
            }
        }
        decays.clear();
        stacks.clear();
        for (Zombie corpse : new ArrayList<>(corpses.values())) {
            GuardianCasts.forget(corpse.getUniqueId());
            corpse.remove();
        }
        corpses.clear();
        corpseOwners.clear();
    }

    private boolean yijkiWeaponImmune(Player player) {
        if (!ActiveSets.has(player, YIJKI)) {
            return false;
        }
        ItemStack held = hands.mainHand(player);
        return held != null && held.getType() == Material.DIAMOND_SWORD
                && CosmicSetCombatListener.weapon(held, YIJKI, views);
    }

    private static Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static double signedHalf() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return (random.nextBoolean() ? -1.0 : 1.0) * random.nextDouble() / 2.0;
    }

    private static Material material(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.GLOWSTONE;
    }

    private static String number(double value) {
        return new DecimalFormat("0.#").format(value);
    }
}
