package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.effect.kind.HeldEnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.soul.SoulService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended native implementations for Cosmic weapon mechanics that cannot be faithfully composed in YAML. */
public final class CosmicWeaponListener implements Listener {

    private static final String DOUBLE_STRIKE = "enchants/double-strike";
    private static final String DOMINATE = "enchants/dominate";
    private static final String CORRUPT = "enchants/corrupt";
    private static final String INVERSION = "enchants/inversion";
    private static final String DIVINE_IMMOLATION = "enchants/divine-immolation";
    private static final String PARALYZE = "enchants/paralyze";
    private static final String THUNDERING_BLOW = "enchants/thundering-blow";
    private static final String CLEAVE = "enchants/cleave";
    private static final String MIGHTY_CLEAVE = "enchants/mighty-cleave";
    private static final ThreadLocal<Integer> DOUBLE_FRAME = ThreadLocal.withInitial(() -> 0);

    private record Route(Player source, Player target) {
    }

    private record Domination(int level, long expiresAt) {
    }

    private record Corruption(int level, long statusExpiresAt, long taskExpiresAt, long token) {
    }

    private record DivineFire(int level, long expiresAt) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final SoulService souls;
    private final LongSupplier nowTicks;
    private final int enchantmentTable;
    private final int portal;
    private final int redstoneDust;
    private final int redstoneWire;
    private final int spell;
    private final int pistonExtend;
    private final int flame;
    private final int lava;
    private final int fireworkBlast;
    private final int pigAngry;
    private final int wither;
    private final int slow;
    private final int miningFatigue;
    private final Map<UUID, Domination> dominations = new ConcurrentHashMap<>();
    private final Map<UUID, Corruption> corruptions = new ConcurrentHashMap<>();
    private final Map<UUID, DivineFire> divineFire = new ConcurrentHashMap<>();
    private final Map<UUID, Long> thunderingUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cleaveAttackerUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cleaveTargetUntil = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong tokens = new java.util.concurrent.atomic.AtomicLong();

    public CosmicWeaponListener(SinkFactory sinks, SinkEnv env, ProtectionService protection,
                                SoulService souls, RegistryResolvers resolvers, LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        this.enchantmentTable = resolvers.particle("ENCHANTMENT_TABLE").orElse(-1);
        this.portal = resolvers.particle("PORTAL").orElse(-1);
        this.redstoneDust = resolvers.particle("REDSTONE").orElse(-1);
        this.redstoneWire = resolvers.material("REDSTONE_WIRE").orElse(-1);
        this.spell = resolvers.particle("SPELL").orElse(-1);
        this.pistonExtend = resolvers.sound("PISTON_EXTEND").orElse(-1);
        this.flame = resolvers.particle("FLAME").orElse(-1);
        this.lava = resolvers.particle("LAVA").orElse(-1);
        this.fireworkBlast = resolvers.sound("FIREWORK_BLAST").orElse(-1);
        this.pigAngry = resolvers.sound("ZOMBIE_PIG_ANGRY").orElse(-1);
        this.wither = resolvers.potionEffect("WITHER").orElse(-1);
        this.slow = resolvers.potionEffect("SLOW").orElse(-1);
        this.miningFatigue = resolvers.potionEffect("SLOW_DIGGING").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCleave(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity primary)
                || event.getDamage() <= 0.0
                || env.stores().suppression().allSuppressed(attacker.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int mighty = CosmicTierGate.tierSixPlusEnabled(attacker)
                ? HeldEnchantLevels.held(attacker, MIGHTY_CLEAVE) : 0;
        int normal = mighty > 0 ? 0 : HeldEnchantLevels.held(attacker, CLEAVE);
        if (mighty <= 0 && normal <= 0) {
            return;
        }
        long now = nowTicks.getAsLong();
        UUID attackerId = attacker.getUniqueId();
        if (cleaveAttackerUntil.getOrDefault(attackerId, 0L) > now) {
            return;
        }
        cleaveAttackerUntil.put(attackerId, now + 30L);

        double radius = mighty > 0 ? 3.0 + 0.25 * mighty : 0.45 * normal;
        double damage = mighty > 0 ? (mighty <= 2 ? 5.0 : mighty == 3 ? 6.0 : 7.0)
                : (normal <= 3 ? 1.0 : normal <= 6 ? 2.0 : 3.0);
        for (Entity nearby : primary.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(attacker)
                    || target.hasMetadata("spectator")
                    || !protection.allows(attackerId, target.getLocation())) {
                continue;
            }
            if (target instanceof Player player && CombatDispatch.friendly(attacker, player)) {
                continue;
            }
            UUID targetId = target.getUniqueId();
            if (cleaveTargetUntil.getOrDefault(targetId, 0L) > now) {
                continue;
            }
            cleaveTargetUntil.put(targetId, now + 20L);
            Scheduling.onEntity(target, () -> {
                if (target.isValid() && !target.isDead()) {
                    target.damage(damage);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOffense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || (event.getDamager() instanceof Projectile projectile
                    && CosmicProjectilePower.weak(projectile.getUniqueId()))) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)
                || CombatDispatch.friendly(attacker, victim)
                || !protection.allows(attacker.getUniqueId(), victim.getLocation())) {
            return;
        }
        runDominate(attacker, victim);
        runCorrupt(attacker, victim);
        runDivineImmolation(attacker, victim);
        runParalyze(attacker, victim);
        runThunderingBlow(attacker, victim);
        runDoubleStrike(event, attacker, victim);
    }

    private void runDominate(Player attacker, Player victim) {
        int level = HeldEnchantLevels.held(attacker, DOMINATE);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 4);
        if (ThreadLocalRandom.current().nextDouble() >= 0.04 * level) {
            return;
        }
        long now = nowTicks.getAsLong();
        Domination current = dominations.get(route.target().getUniqueId());
        if (current != null && current.expiresAt() > now && current.level() > level) {
            return;
        }
        boolean first = current == null || current.expiresAt() <= now;
        int duration = level * 40;
        dominations.put(route.target().getUniqueId(), new Domination(level, now + duration));
        env.stores().outgoingDebuff().weaken(route.target().getUniqueId(), level * 5.0, now, duration);

        SinkReadback sink = sinks.create(env);
        if (first) {
            sink.message(route.target(), "&c&l* DOMINATED [&c-" + (level * 5) + "% DMG for "
                    + (level * 2) + "s&c&l] *");
        }
        if (enchantmentTable >= 0) {
            sink.particle(route.target().getLocation().clone().add(0.0, 1.0, 0.0), enchantmentTable,
                    32, -1, 0.8, 0.8, 0.8, 0.0);
        }
        sink.flush();
    }

    private void runCorrupt(Player attacker, Player victim) {
        int level = HeldEnchantLevels.held(attacker, CORRUPT);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 4);
        // Source wrote 5.0 + 0.02L (always true); intended decimal probability is 0.05 + 0.02L.
        if (ThreadLocalRandom.current().nextDouble() >= 0.05 + 0.02 * level) {
            return;
        }
        long now = nowTicks.getAsLong();
        Corruption current = corruptions.get(route.target().getUniqueId());
        if (current != null && current.statusExpiresAt() > now) {
            return;
        }
        long token = tokens.incrementAndGet();
        long statusExpiry = now + level * 40L;
        long taskExpiry = current != null && current.taskExpiresAt() > now
                ? current.taskExpiresAt() : now + level * level * 20L;
        Corruption next = new Corruption(level, statusExpiry, taskExpiry, token);
        corruptions.put(route.target().getUniqueId(), next);

        SinkReadback sink = sinks.create(env);
        if (portal >= 0) {
            sink.particle(route.target().getEyeLocation(), portal, 20, -1, 0.6, 0.6, 0.6, 0.0);
        }
        sink.flush();

        if (HeldEnchantLevels.held(route.target(), INVERSION) > 0
                || (current != null && current.taskExpiresAt() > now)) {
            return;
        }
        tickCorrupt(route.target(), next, level + 1);
    }

    private void tickCorrupt(Player target, Corruption state, int remaining) {
        if (remaining <= 0 || !target.isOnline() || target.isDead()) {
            finishCorruptTask(target.getUniqueId(), state);
            return;
        }
        Corruption live = corruptions.get(target.getUniqueId());
        if (live == null || live.taskExpiresAt() != state.taskExpiresAt()) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.damage(target, state.level() >= 3 ? 2.0 : 1.0);
        if (redstoneWire >= 0) {
            sink.blockBreakEffect(target, redstoneWire, "feet", 1.0);
        }
        sink.flush();
        Scheduling.onEntityLater(target, state.level() * 20L,
                () -> tickCorrupt(target, state, remaining - 1));
    }

    private void finishCorruptTask(UUID player, Corruption state) {
        corruptions.computeIfPresent(player, (id, live) -> live.taskExpiresAt() == state.taskExpiresAt()
                ? new Corruption(live.level(), live.statusExpiresAt(), 0L, live.token()) : live);
    }

    private void runDoubleStrike(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        int level = HeldEnchantLevels.held(attacker, DOUBLE_STRIKE);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 5);
        if (ThreadLocalRandom.current().nextDouble() >= 0.02 * level) {
            return;
        }
        double damage = event.getFinalDamage() == 0.0 ? event.getDamage() : event.getFinalDamage();
        SinkReadback sink = sinks.create(env);
        if (redstoneDust >= 0) {
            sink.particle(route.target().getLocation(), redstoneDust, 20, -1,
                    1.0, 1.0, 1.0, 0.5);
        }
        sink.flush();
        Scheduling.onEntityLater(route.target(), 2L, () -> {
            if (!route.target().isValid() || route.target().isDead() || route.target().getHealth() <= 0.0) {
                return;
            }
            env.stores().vars().set(route.source().getUniqueId(), "no-rage", "1", nowTicks.getAsLong(), 1);
            DOUBLE_FRAME.set(DOUBLE_FRAME.get() + 1);
            try {
                route.target().damage(damage, route.source());
            } finally {
                int depth = DOUBLE_FRAME.get() - 1;
                if (depth <= 0) {
                    DOUBLE_FRAME.remove();
                } else {
                    DOUBLE_FRAME.set(depth);
                }
            }
        });
    }

    private void runParalyze(Player attacker, Player victim) {
        int level = HeldEnchantLevels.held(attacker, PARALYZE);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 3);
        double chance = level == 3 ? 0.05 : 0.0175 * level;
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        org.bukkit.Location strike = paralyzeStrikeLocation(route.source(), route.target());
        Scheduling.onRegionLater(strike, 0L, () -> strike.getWorld().strikeLightningEffect(strike));
        SinkReadback sink = sinks.create(env);
        if (slow >= 0) {
            sink.potion(route.target(), slow, level > 2 ? 1 : 0, 100);
        }
        if (level == 4 && miningFatigue >= 0) {
            sink.potion(route.target(), miningFatigue, 1, 100);
        }
        sink.flush();
        Scheduling.onEntity(route.target(), () -> route.target().damage(1.0 + level));
    }

    static org.bukkit.Location paralyzeStrikeLocation(Player source, Player target) {
        Objects.requireNonNull(source, "source");
        return Objects.requireNonNull(target, "target").getLocation().clone();
    }

    private void runThunderingBlow(Player attacker, Player victim) {
        int level = HeldEnchantLevels.held(attacker, THUNDERING_BLOW);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 1);
        double chance = level == 3 ? 0.05 : 0.0175 * level;
        long now = nowTicks.getAsLong();
        UUID targetId = route.target().getUniqueId();
        if (ThreadLocalRandom.current().nextDouble() >= chance
                || thunderingUntil.getOrDefault(targetId, 0L) > now) {
            return;
        }
        thunderingUntil.put(targetId, now + 50L);
        org.bukkit.Location strike = route.target().getLocation().clone();
        Scheduling.onRegionLater(strike, 0L, () -> strike.getWorld().strikeLightningEffect(strike));
        Scheduling.onEntity(route.target(), () -> route.target().damage(5.0));
    }

    private void runDivineImmolation(Player attacker, Player victim) {
        if (!CosmicTierGate.tierSixPlusEnabled(attacker)) {
            return;
        }
        int level = HeldEnchantLevels.held(attacker, DIVINE_IMMOLATION);
        if (level <= 0) {
            return;
        }
        Route route = reflect(attacker, victim, level, 6);
        long now = nowTicks.getAsLong();
        if (!engine.sink.HeldChanges.settled(route.source().getUniqueId(), now, 5)
                || env.stores().vars().get(route.source().getUniqueId(), "soul-trapped", now) != null
                || (!souls.costFree(route.source())
                && (!souls.active(route.source()) || souls.carriedTotal(route.source()) <= 0))) {
            return;
        }
        if (env.stores().vars().get(route.source().getUniqueId(), "last-soul-remove", now) == null) {
            env.stores().vars().set(route.source().getUniqueId(), "last-soul-remove", "1", now, 20);
            souls.drainUpTo(route.source(), 20);
        }

        double radius = level;
        double damage = Math.floor(level * 1.25);
        java.util.List<Entity> candidates = new java.util.ArrayList<>(
                route.target().getNearbyEntities(radius, radius, radius));
        candidates.add(route.target());
        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity target) || target.equals(route.source())
                    || target.hasMetadata("spectator")
                    || !protection.allows(route.source().getUniqueId(), target.getLocation())) {
                continue;
            }
            if (target instanceof Player player && (player.getGameMode() != org.bukkit.GameMode.SURVIVAL
                    || CombatDispatch.friendly(route.source(), player) || !route.source().canSee(player))) {
                continue;
            }
            Scheduling.onEntity(target, () -> target.damage(damage));
            SinkReadback sink = sinks.create(env);
            if (flame >= 0) {
                sink.particle(target.getEyeLocation(), flame, 30, -1, 0.15, 0.15, 0.15, 0.0);
            }
            if (lava >= 0) {
                sink.particle(target.getEyeLocation(), lava, 20, -1, 0.5, 0.5, 0.5, 0.0);
            }
            if (wither >= 0) {
                sink.potion(target, wither, 1, (int) (damage * 20.0));
            }
            if (target instanceof Player player) {
                if (fireworkBlast >= 0) {
                    sink.privateSound(player, fireworkBlast, 1.0f, 0.3f);
                }
                if (pigAngry >= 0) {
                    sink.privateSound(player, pigAngry, 0.8f, 0.5f);
                }
            }
            sink.flush();
            divineFire.put(target.getUniqueId(), new DivineFire(level, now + (4L + level) * 20L));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDivineWither(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.WITHER
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        DivineFire fire = divineFire.get(target.getUniqueId());
        long now = nowTicks.getAsLong();
        if (fire == null) {
            return;
        }
        if (fire.expiresAt() <= now) {
            divineFire.remove(target.getUniqueId(), fire);
            return;
        }
        event.setCancelled(true);
        Scheduling.onEntity(target, () -> target.damage(Math.min(7.0, 4.0 + fire.level())));
        SinkReadback sink = sinks.create(env);
        if (flame >= 0) {
            sink.particle(target.getEyeLocation(), flame, 20, -1, 0.15, 0.15, 0.15, 0.0);
        }
        if (lava >= 0) {
            sink.particle(target.getEyeLocation(), lava, 15, -1, 0.5, 0.5, 0.5, 0.0);
        }
        if (target instanceof Player player) {
            sink.message(player, "&c&l** DIVINE IMMOLATION **");
            if (pigAngry >= 0) {
                sink.privateSound(player, pigAngry, 0.6f, 0.8f);
            }
        }
        sink.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInversion(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int level = HeldEnchantLevels.held(wearer, INVERSION);
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() > (float) (0.05 * level)) {
            return;
        }
        double amount = ThreadLocalRandom.current().nextInt(1, 4);
        Corruption corruption = corruptions.get(wearer.getUniqueId());
        long now = nowTicks.getAsLong();
        SinkReadback sink = sinks.create(env);
        if (corruption != null && corruption.statusExpiresAt() > now
                && ThreadLocalRandom.current().nextDouble() < (float) corruption.level() * 0.2f) {
            sink.damage(wearer, amount);
            sink.message(wearer, "&5* CORRUPTED [&c" + amount + "&5 DMG] *");
            sink.flush();
            return;
        }
        event.setDamage(0.0);
        event.setCancelled(true);
        sink.heal(wearer, amount);
        if (spell >= 0) {
            sink.particle(wearer.getLocation().clone().add(0.0, 1.0, 0.0), spell,
                    20, -1, 0.45, 0.45, 0.45, 0.0);
        }
        if (pistonExtend >= 0) {
            sink.privateSound(wearer, pistonExtend, 0.8f, 2.0f);
        }
        sink.flush();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        dominations.remove(id);
        corruptions.remove(id);
        divineFire.remove(id);
        thunderingUntil.remove(id);
        cleaveAttackerUntil.remove(id);
        cleaveTargetUntil.remove(id);
    }

    public void stop() {
        dominations.clear();
        corruptions.clear();
        divineFire.clear();
        thunderingUntil.clear();
        cleaveAttackerUntil.clear();
        cleaveTargetUntil.clear();
    }

    static boolean doubleStrikeActive() {
        return DOUBLE_FRAME.get() > 0;
    }

    private Route reflect(Player attacker, Player victim, int enchantLevel, int tier) {
        int normal = EnchantLevels.worn(victim, "enchants/enchant-reflect");
        int heroic = CosmicTierGate.tierSixPlusEnabled(victim)
                ? EnchantLevels.worn(victim, "enchants/heroic-enchant-reflect") : 0;
        int reflect = heroic > 0 && tier <= 7 && heroic >= enchantLevel ? heroic
                : normal > 0 && tier <= 5 && normal >= enchantLevel ? normal : 0;
        if (reflect > 0 && ThreadLocalRandom.current().nextDouble()
                <= 0.02 + 0.01 * (reflect / 3)) {
            return new Route(victim, attacker);
        }
        return new Route(attacker, victim);
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
