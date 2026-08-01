package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended gameplay implementation of Cosmic Ghost mastery Feign Death. */
public final class FeignDeathListener implements Listener {

    private static volatile FeignDeathListener active;
    private static final String ENCHANT = "enchants/feign-death";
    private static final long COOLDOWN_TICKS = 200L;

    private record Vanish(long token, long untilTick, int maxHits, int hits) {
        Vanish hit() {
            return new Vanish(token, untilTick, maxHits, hits + 1);
        }
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final LongSupplier nowTicks;
    private final int witherShoot;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Vanish> vanished = new ConcurrentHashMap<>();
    private final AtomicLong tokens = new AtomicLong();

    public FeignDeathListener(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers,
                              LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.witherShoot = Objects.requireNonNull(resolvers, "resolvers")
                .sound("WITHER_SHOOT").orElse(-1);
        active = this;
    }

    /** Whether the shared armour-mastery/pet 10-second gate permits a pet activation now. */
    public static boolean petReady(Player player) {
        FeignDeathListener listener = active;
        return listener != null && listener.cooldownUntil.getOrDefault(player.getUniqueId(), 0L)
                <= listener.nowTicks.getAsLong();
    }

    /** Guaranteed pet activation through the same vanish/hit-count state machine as the armour mastery. */
    public static boolean activatePet(Player player, int level) {
        FeignDeathListener listener = active;
        if (listener == null || level < 1 || level > 4 || !petReady(player)) {
            return false;
        }
        listener.vanish(player, level, listener.nowTicks.getAsLong());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)
                || !CosmicTierGate.tierSixPlusEnabled(damaged)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(damaged, env)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        int level = EnchantLevels.worn(damaged, ENCHANT);
        long now = nowTicks.getAsLong();
        if (level <= 0 || level > 4 || damaged.isDead() || damaged.getHealth() <= event.getFinalDamage()
                || cooldownUntil.getOrDefault(damaged.getUniqueId(), 0L) > now) {
            return;
        }
        double healthRatio = damaged.getHealth() / damaged.getMaxHealth();
        double chancePercent = level + (1.0 - healthRatio) * 5.0;
        if (ThreadLocalRandom.current().nextDouble() > chancePercent * 0.01) {
            return;
        }
        vanish(damaged, level, now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player ghost)) {
            return; // source counts direct player hits only, not bow/projectile damage.
        }
        UUID playerId = ghost.getUniqueId();
        Vanish current = vanished.get(playerId);
        if (current == null) {
            return;
        }
        long now = nowTicks.getAsLong();
        if (current.untilTick() <= now) {
            unvanish(ghost, current.token());
            return;
        }
        Vanish next = current.hit();
        if (next.hits() >= next.maxHits()) {
            unvanish(ghost, current.token());
            return; // the revealing hit itself does not print the Ghost damage line in the source.
        }
        if (!vanished.replace(playerId, current, next)) {
            return;
        }
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0.0) {
            SinkReadback sink = sinks.create(env);
            sink.message(victim, "&c-" + oneDecimal(event.getFinalDamage()) + " HP (&f"
                    + ghost.getName() + "'s Ghost&c)");
            sink.flush();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Vanish current = vanished.get(event.getEntity().getUniqueId());
        if (current != null) {
            unvanish(event.getEntity(), current.token());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Vanish current = vanished.get(event.getPlayer().getUniqueId());
        if (current != null) {
            unvanish(event.getPlayer(), current.token());
        }
        long now = nowTicks.getAsLong();
        cooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void vanish(Player player, int level, long now) {
        int duration = level * 30; // 1.5 seconds per level: 30/60/90/120 ticks.
        long token = tokens.incrementAndGet();
        vanished.put(player.getUniqueId(), new Vanish(token, now + duration, level, 0));
        cooldownUntil.put(player.getUniqueId(), now + COOLDOWN_TICKS);

        String seconds = oneDecimal(level * 1.5);
        SinkReadback sink = sinks.create(env);
        sink.fakeDeath(player);
        sink.title(player, "&c&lFeign Death", "&c" + seconds, 10, 70, 20);
        sink.hiddenFromOnline(player, true);
        if (witherShoot >= 0) {
            sink.privateSound(player, witherShoot, 3.0f, 0.9f);
        }
        sink.message(player, "&4&l* Feign Death - VANISHED [" + seconds + "s] *");
        sink.flush();

        Scheduling.onEntityLater(player, duration, () -> unvanish(player, token));
    }

    private void unvanish(Player player, long token) {
        UUID playerId = player.getUniqueId();
        Vanish current = vanished.get(playerId);
        if (current == null || current.token() != token || !vanished.remove(playerId, current)) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.hiddenFromOnline(player, false);
        sink.message(player, "&4&l* Feign Death - UNVANISHED *");
        sink.flush();
    }

    public void stop() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            Vanish current = vanished.get(player.getUniqueId());
            if (current != null) {
                unvanish(player, current.token());
            }
        }
        vanished.clear();
        cooldownUntil.clear();
        if (active == this) {
            active = null;
        }
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

    private static String oneDecimal(double value) {
        return new DecimalFormat("#.#").format(value);
    }
}
