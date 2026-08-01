package feature.combat;

import engine.effect.kind.ActiveMasks;
import engine.effect.kind.EnchantLevels;
import engine.sink.PotionReductions;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended implementations of Cosmic's remaining Death Knight/Necromancer combat masteries. */
public final class CosmicMasteryListener implements Listener {

    private static final String CHAIN = "enchants/chain-lifesteal";
    private static final String DEATH_PACT = "enchants/death-pact";
    private static final String MORTAL_COIL = "enchants/mortal-coil";
    private static final String PERMAFROST = "enchants/permafrost";
    private static final String NECROMANCER_MASK = "masks/necromancer-mask";
    private static final String LOVER_MASK = "masks/lover-mask";
    private static final String SANTA_MASK = "masks/santa";
    private static final int PERMANENT_POTION_TICKS = Integer.MAX_VALUE;

    private record Frozen(BlockState original, UUID owner, int level, long token) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final LongSupplier nowTicks;
    private final int redstoneBlock;
    private final int healthBoost;
    private final int miningFatigue;
    private final int glass;
    private final Map<UUID, Long> mortalUntil = new ConcurrentHashMap<>();
    private final Map<Block, Frozen> frozen = new ConcurrentHashMap<>();
    private final AtomicLong freezeTokens = new AtomicLong();

    public CosmicMasteryListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                                 RegistryResolvers resolvers, LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.redstoneBlock = resolvers.material("REDSTONE_BLOCK").orElse(-1);
        this.healthBoost = resolvers.potionEffect("HEALTH_BOOST").orElse(-1);
        this.miningFatigue = resolvers.potionEffect("SLOW_DIGGING").orElse(-1);
        this.glass = resolvers.sound("GLASS").orElse(-1);
    }

    /** Mastery offensive hooks run after the normal hit and include projectile shooters. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOffense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event);
        if (attacker == null || attacker.equals(victim) || !CosmicTierGate.tierSixPlusEnabled(attacker)) {
            return;
        }

        runDeathPact(event, attacker, victim);
        runChainLifesteal(event, attacker, victim);
        runMortalCoil(event, attacker, victim);
    }

    private void runDeathPact(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        int level = EnchantLevels.worn(attacker, DEATH_PACT);
        if (level <= 0) {
            return;
        }
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.route(attacker, victim, level);
        if (route.blocked()) {
            return;
        }
        double missing = missingHealth(route.source());
        double reduction = Math.min(25.0, (2.0 + level * 2.0) * (missing / 0.1));
        event.setDamage(event.getDamage() * (1.0 - reduction * 0.01));
    }

    private void runChainLifesteal(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        int level = EnchantLevels.worn(attacker, CHAIN);
        if (level <= 0) {
            return;
        }
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.route(attacker, victim, level);
        if (route.blocked() || ThreadLocalRandom.current().nextDouble() > level * 0.05
                || immuneNecromancer(route.target()) || !canAffect(route.source(), route.target())) {
            return;
        }

        int radius = 1 + level / 2;
        int maxTargets = chainTargetCap(level);
        List<Entity> candidates = new ArrayList<>(route.target().getNearbyEntities(radius, radius, radius));
        candidates.add(route.target());
        int hit = 0;
        SinkReadback sink = sinks.create(env);
        for (Entity entity : candidates) {
            if (!(entity instanceof Player candidate) || candidate.equals(route.source())
                    || immuneNecromancer(candidate) || !canAffect(route.source(), candidate)
                    || CombatDispatch.friendly(route.source(), candidate)) {
                continue;
            }
            if (hit >= maxTargets) {
                break;
            }
            hit++;
            double amount = 0.5 + ThreadLocalRandom.current().nextDouble() * level;
            boolean lethal = candidate.getHealth() <= amount;
            sink.setHealth(candidate, Math.max(0.0, candidate.getHealth() - amount));
            sink.heal(route.source(), amount); // intended Chain Lifesteal: the enchant wearer receives the drain.
            if (redstoneBlock >= 0) {
                sink.blockBreakEffect(candidate, redstoneBlock, "eye", 0.0);
            }
            if (!lethal) {
                sink.hurtAnimation(candidate);
            } else {
                event.setCancelled(true);
                break;
            }
        }
        sink.flush();
    }

    private void runMortalCoil(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        int level = EnchantLevels.worn(attacker, MORTAL_COIL);
        if (level <= 0) {
            return;
        }
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.route(attacker, victim, level);
        long now = nowTicks.getAsLong();
        if (route.blocked() || mortalUntil.getOrDefault(route.target().getUniqueId(), 0L) > now
                || ThreadLocalRandom.current().nextDouble() > 0.025 + level * 0.015) {
            return;
        }
        Player target = route.target();
        if (ActiveMasks.has(target, LOVER_MASK)) {
            return;
        }

        int duration = (int) Math.round((2.0 + level * 0.4) * 20.0);
        mortalUntil.put(target.getUniqueId(), now + duration);
        SinkReadback sink = sinks.create(env);
        sink.message(target, "&c&l* MORTAL COIL " + roman(level) + " (&7" + route.source().getName()
                + " [" + oneDecimal(duration / 20.0) + "s]&c&l) *");

        if (healthBoost >= 0) {
            PotionEffect current = activePotion(target, PotionEffectType.HEALTH_BOOST);
            PotionReductions.reduce(target.getUniqueId(), "HEALTH_BOOST", level + 1, duration * 50L);
            if (current != null) {
                sink.potionForce(target, healthBoost, current.getAmplifier(), Math.max(1, current.getDuration()));
            } else {
                target.removePotionEffect(PotionEffectType.HEALTH_BOOST);
            }
        }
        if (ActiveMasks.has(target, SANTA_MASK)) {
            sink.drainMaxHealth(target, 0.0, 0.0, 4.0, duration);
        }
        sink.flush();

        Scheduling.onEntityLater(target, duration + 1L, () -> {
            mortalUntil.remove(target.getUniqueId(), now + duration);
            restoreWornHealthBoost(target);
        });
    }

    /** Death Pact's defensive half is intentionally unconditional and uses all damage causes. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeathPactDefense(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int level = EnchantLevels.worn(wearer, DEATH_PACT);
        if (level <= 0) {
            return;
        }
        double reduction = Math.min(50.0, level * 2.0 * (missingHealth(wearer) / 0.1));
        event.setDamage(event.getDamage() * (1.0 - reduction * 0.01));
    }

    /** Permafrost's defensive proc: form a temporary snow floor from valid nearby surface blocks. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPermafrostProc(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int level = EnchantLevels.worn(wearer, PERMAFROST);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() > 0.02 + level / 3.0 * 0.01) {
            return;
        }

        int radius = Math.min(2 + level, 5);
        int seconds = (int) Math.ceil(4.0 + level / 3.0);
        Block center = wearer.getLocation().getBlock();
        List<Block> changed = new ArrayList<>();
        long token = freezeTokens.incrementAndGet();
        int radiusSquared = radius * radius;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - 2; y < center.getY(); y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    if (block.getLocation().distanceSquared(center.getLocation()) > radiusSquared
                            || frozen.containsKey(block) || !block.getType().isBlock() || !block.getType().isSolid()
                            || !block.getRelative(0, 1, 0).isEmpty()
                            || !protection.allows(wearer.getUniqueId(), block.getLocation())) {
                        continue;
                    }
                    Frozen value = new Frozen(block.getState(), wearer.getUniqueId(), level, token);
                    if (frozen.putIfAbsent(block, value) == null) {
                        block.setType(Material.SNOW_BLOCK, false);
                        changed.add(block);
                        if (ThreadLocalRandom.current().nextDouble() <= 0.4) {
                            wearer.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, Material.SNOW_BLOCK);
                        }
                    }
                }
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.message(wearer, "&c&l* PERMAFROST [&7" + seconds + "s&c&l] *");
        if (glass >= 0) {
            sink.sound(wearer, glass, 3.0f, 1.1f);
        }
        sink.flush();
        Scheduling.onEntityLater(wearer, seconds * 20L, () -> restoreBatch(changed, token));
    }

    /** While standing on their own Permafrost, the owner takes 14+level percent less PvP damage. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !CosmicTierGate.tierSixPlusEnabled(victim)) {
            return;
        }
        Player attacker = attackingPlayer(event);
        if (attacker == null) {
            return;
        }
        Frozen value = frozen.get(victim.getLocation().getBlock().getRelative(0, -1, 0));
        if (value == null || !value.owner().equals(victim.getUniqueId())) {
            return;
        }
        event.setDamage(event.getDamage() * (1.0 - (14.0 + value.level()) * 0.01));
        if (miningFatigue >= 0) {
            SinkReadback sink = sinks.create(env);
            sink.potionForce(attacker, miningFatigue, 1, 50);
            sink.flush();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (frozen.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (frozen.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        mortalUntil.remove(id);
        PotionReductions.clear(id);
    }

    public void stop() {
        for (Map.Entry<Block, Frozen> entry : new ArrayList<>(frozen.entrySet())) {
            if (frozen.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().original().update(true, false);
            }
        }
        mortalUntil.clear();
        PotionReductions.clearAll();
    }

    private void restoreBatch(List<Block> changed, long token) {
        for (Block block : changed) {
            Frozen value = frozen.get(block);
            if (value != null && value.token() == token && frozen.remove(block, value)) {
                value.original().update(true, false);
            }
        }
    }

    private void restoreWornHealthBoost(Player target) {
        if (healthBoost < 0 || !target.isOnline()) {
            return;
        }
        int overload = EnchantLevels.worn(target, "enchants/overload");
        int godly = CosmicTierGate.tierSixPlusEnabled(target)
                ? EnchantLevels.worn(target, "enchants/godly-overload") : 0;
        int amplifier = wornHealthBoostAmplifier(overload, godly);
        if (amplifier >= 0) {
            SinkReadback sink = sinks.create(env);
            sink.potionForce(target, healthBoost, amplifier, PERMANENT_POTION_TICKS);
            sink.flush();
        }
    }

    static int wornHealthBoostAmplifier(int overloadLevel, int godlyOverloadLevel) {
        // Authored potion levels are Overload L and Godly Overload L+3; Bukkit amplifiers are one lower.
        return Math.max(overloadLevel - 1, godlyOverloadLevel <= 0 ? -1 : godlyOverloadLevel + 2);
    }

    static int chainTargetCap(int level) {
        // Intended 1 + ceil(level / 2.0); Cosmic accidentally divided as an int before calling ceil.
        return 1 + (level + 1) / 2;
    }

    private boolean canAffect(Player source, Player target) {
        return target.getGameMode() == GameMode.SURVIVAL
                && !target.hasMetadata("spectator") && !target.hasMetadata("NPC")
                && !target.hasMetadata("vanished") && !target.hasMetadata("god") && !target.hasMetadata("godmode")
                && protection.allows(source.getUniqueId(), target.getLocation());
    }

    private static boolean immuneNecromancer(Player player) {
        return ActiveMasks.has(player, NECROMANCER_MASK);
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

    private static PotionEffect activePotion(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) {
                return effect;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static double missingHealth(Player player) {
        double max = player.getMaxHealth();
        return max <= 0.0 ? 0.0 : Math.max(0.0, max - player.getHealth()) / max;
    }

    private static String oneDecimal(double value) {
        return new DecimalFormat("0.#").format(value);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }
}
