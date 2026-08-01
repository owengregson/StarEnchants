package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;

/** Native, bug-fixed implementation of Cosmic's Self Destruct armor enchant. */
public final class CosmicSelfDestructListener implements Listener {

    private static final String ENCHANT = "enchants/self-destruct";
    private static final long COOLDOWN_MILLIS = 10_000L;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final LongSupplier nowMillis;
    private final Map<UUID, Long> lastActivation = new ConcurrentHashMap<>();
    private final Map<UUID, OwnedTnt> ownedTnt = new ConcurrentHashMap<>();
    private final int explodeSound;
    private final int largeExplosion;

    public CosmicSelfDestructListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                                      RegistryResolvers resolvers) {
        this(sinks, env, protection, resolvers, System::currentTimeMillis);
    }

    CosmicSelfDestructListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                               RegistryResolvers resolvers, LongSupplier nowMillis) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
        Objects.requireNonNull(resolvers, "resolvers");
        explodeSound = resolvers.sound("EXPLODE").orElse(-1);
        largeExplosion = resolvers.particle("LARGE_EXPLODE").orElse(-1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || CosmicDefenseGate.silenced(wearer, env)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 3) {
            return;
        }

        // Cosmic deliberately recalculated the raw hit through its 1.8 armor-value table here.
        double lethalDamage = event.getDamage() * armorNullification(wearer);
        if (wearer.getHealth() - lethalDamage > 0.0) {
            return;
        }

        long now = nowMillis.getAsLong();
        Long last = lastActivation.get(wearer.getUniqueId());
        if (last != null && now - last <= COOLDOWN_MILLIS) {
            return;
        }
        lastActivation.put(wearer.getUniqueId(), now);

        int countAndWarningRadius = (int) (level * 2.5f);
        warnEnemies(wearer, countAndWarningRadius);

        SinkReadback activation = sinks.create(env);
        if (explodeSound >= 0) {
            activation.sound(wearer.getLocation(), explodeSound, 2.0f, 0.75f);
        }
        activation.flush();

        int fuseTicks = 120 - level * 20;
        for (int index = 0; index < countAndWarningRadius; index++) {
            spawnTnt(wearer, nearbyAir(wearer.getLocation(), 3, 1), fuseTicks);
        }
    }

    private void warnEnemies(Player owner, int radius) {
        SinkReadback sink = sinks.create(env);
        for (Entity nearby : owner.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player && !CombatDispatch.friendly(owner, player)) {
                sink.message(player, "&c&l(!) &c" + owner.getName()
                        + "'s Self-Destruct was activated, RUN!");
            }
        }
        sink.flush();
    }

    private void spawnTnt(Player owner, Location location, int fuseTicks) {
        if (location.getBlock().getType().isSolid()) {
            return;
        }
        Entity spawned = location.getWorld().spawnEntity(location, primedTntType());
        if (!(spawned instanceof TNTPrimed tnt)) {
            spawned.remove();
            return;
        }
        tnt.setFuseTicks(fuseTicks);
        ownedTnt.put(tnt.getUniqueId(), new OwnedTnt(owner.getUniqueId(), tnt));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();
        if (source == null) {
            return;
        }
        OwnedTnt state = ownedTnt.remove(source.getUniqueId());
        if (state == null) {
            return;
        }

        event.setCancelled(true);
        event.setYield(0.0f);
        event.blockList().clear();

        Location at = event.getLocation();
        if (!protection.allows(state.owner(), at)) {
            source.remove();
            return;
        }

        SinkReadback sink = sinks.create(env);
        if (explodeSound >= 0) {
            sink.sound(at, explodeSound, 1.0f, 1.0f);
        }
        if (largeExplosion >= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            sink.particle(at, largeExplosion, 3, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }

        Player owner = Bukkit.getPlayer(state.owner());
        for (Entity nearby : source.getNearbyEntities(4.0, 4.0, 4.0)) {
            if (!(nearby instanceof LivingEntity living) || living.hasMetadata("spectator")
                    || living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR
                    || living instanceof Player player && owner != null && CombatDispatch.friendly(owner, player)
                    || !protection.allows(state.owner(), living.getLocation())) {
                continue;
            }
            sink.damage(living, 16.0);
            sink.ignite(living, 40);
            // Intended bug fix: Cosmic inverted these arguments and pushed the TNT instead of the victim.
            sink.knockback(living, at, 1.7);
        }
        source.remove();
        sink.flush();
    }

    public void stop() {
        for (OwnedTnt state : ownedTnt.values()) {
            TNTPrimed tnt = state.entity();
            if (tnt.isValid()) {
                tnt.remove();
            }
        }
        ownedTnt.clear();
        lastActivation.clear();
    }

    static int countForLevel(int level) {
        return (int) (level * 2.5f);
    }

    static int fuseForLevel(int level) {
        return 120 - level * 20;
    }

    private static Location nearbyAir(Location origin, int radius, int yBoost) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = (random.nextBoolean() ? 1 : -1) * random.nextInt(1, radius + 1);
        int z = (random.nextBoolean() ? 1 : -1) * random.nextInt(1, radius + 1);
        Location location = origin.clone().add(x + 0.5, yBoost, z + 0.5);
        int ceiling = Math.min(255, location.getWorld().getMaxHeight() - 1);
        while ((location.getBlock().getType() != Material.AIR
                || location.getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR)
                && location.getY() < ceiling) {
            location.add(0.0, 1.0, 0.0);
        }
        return location;
    }

    private static double armorNullification(Player player) {
        int armor = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) {
                continue;
            }
            String name = piece.getType().name();
            if (name.contains("LEATHER_")) {
                armor += name.contains("CHESTPLATE") ? 3 : name.contains("LEGGINGS") ? 2 : 1;
            } else if (name.contains("CHAINMAIL_")) {
                armor += name.contains("CHESTPLATE") ? 5 : name.contains("LEGGINGS") ? 4
                        : name.contains("HELMET") ? 2 : 1;
            } else if (name.contains("IRON_")) {
                armor += name.contains("CHESTPLATE") ? 6 : name.contains("LEGGINGS") ? 5 : 2;
            } else if (name.contains("DIAMOND_")) {
                armor += name.contains("CHESTPLATE") ? 8 : name.contains("LEGGINGS") ? 6 : 3;
            } else if (name.contains("GOLD_") || name.contains("GOLDEN_")) {
                armor += name.contains("CHESTPLATE") ? 5 : name.contains("LEGGINGS") ? 3
                        : name.contains("HELMET") ? 2 : 1;
            }
        }
        return 1.0 - armor * 0.04;
    }

    private static EntityType primedTntType() {
        try {
            return EntityType.valueOf("PRIMED_TNT");
        } catch (IllegalArgumentException absentLegacyName) {
            return EntityType.valueOf("TNT");
        }
    }

    private record OwnedTnt(UUID owner, TNTPrimed entity) {
    }
}
