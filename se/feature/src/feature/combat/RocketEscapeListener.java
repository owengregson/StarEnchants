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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Native exact implementation shared by Rocket Escape and its heroic Guided version. */
public final class RocketEscapeListener implements Listener {

    private static final String NORMAL = "enchants/rocket-escape";
    private static final String GUIDED = "enchants/guided-rocket-escape";
    private static final long NORMAL_COOLDOWN = 600L;
    private static final long GUIDED_COOLDOWN = 300L;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final LongSupplier nowTicks;
    private final int slow;
    private final int fatigue;
    private final int regeneration;
    private final int cloud;
    private final int explode;
    private final Map<UUID, Long> lastActivation = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> noFall = new ConcurrentHashMap<>();

    public RocketEscapeListener(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers,
                                LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        Objects.requireNonNull(resolvers, "resolvers");
        slow = resolvers.potionEffect("SLOW").orElse(-1);
        fatigue = resolvers.potionEffect("SLOW_DIGGING").orElse(-1);
        regeneration = resolvers.potionEffect("REGENERATION").orElse(-1);
        cloud = resolvers.particle("CLOUD").orElse(-1);
        explode = resolvers.sound("EXPLODE").orElse(-1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || CosmicDefenseGate.silenced(player, env)
                || player.getHealth() - event.getFinalDamage() > 0.0) {
            return;
        }
        int guided = CosmicTierGate.tierSixPlusEnabled(player) ? EnchantLevels.worn(player, GUIDED) : 0;
        int normal = EnchantLevels.worn(player, NORMAL);
        boolean heroic = guided > 0;
        int level = heroic ? guided : normal;
        if (level <= 0 || level > 3 || blockedWorld(player)) {
            return;
        }

        long now = nowTicks.getAsLong();
        long cooldown = heroic ? GUIDED_COOLDOWN : NORMAL_COOLDOWN;
        Long last = lastActivation.get(player.getUniqueId());
        if (last != null && now - last <= cooldown) { // source uses elapsed > cooldown, so the endpoint is blocked.
            return;
        }
        // The source stamps cooldown before rolling Sabotage; a successfully sabotaged escape still consumes it.
        lastActivation.put(player.getUniqueId(), now);
        String sabotaged = env.stores().vars().get(player.getUniqueId(), "sabotage-level", now);
        if (sabotaged != null) {
            int sabotageLevel;
            try {
                sabotageLevel = Integer.parseInt(sabotaged);
            } catch (NumberFormatException ignored) {
                sabotageLevel = 0;
            }
            if (sabotageLevel > 0 && ThreadLocalRandom.current().nextDouble() < sabotageLevel * 0.10) {
                SinkReadback sink = sinks.create(env);
                sink.message(player, "&c&l ** &7" + (heroic ? "Guided Rocket Escape" : "Rocket Escape")
                        + ":&c&l SABOTAGED **");
                sink.flush();
                return;
            }
        }

        event.setCancelled(true);
        event.setDamage(0.0);
        noFall.put(player.getUniqueId(), Boolean.TRUE);

        SinkReadback sink = sinks.create(env);
        sink.message(player, "");
        if (heroic) {
            sink.message(player, "&a&l(!) &aYour Guided Rocket Escape boots have activated, flight temporarily enabled, recover while they last!");
        } else {
            sink.message(player, "&a&l(!) &aYour Rocket Escape boots have activated, recover while they last!");
        }
        sink.message(player, "");
        if (explode >= 0) {
            sink.privateSound(player, explode, 1.0f, 0.54f);
        }
        double launchY = (player.getWorld().getName().equals("world_duels")
                || player.getWorld().getName().equals("world_duels2")) ? 2 + level : 4 + level * 2;
        sink.setVelocity(player, 0.0, launchY, 0.0);
        if (slow >= 0) {
            sink.removePotion(player, slow);
        }
        if (fatigue >= 0) {
            sink.removePotion(player, fatigue);
        }
        if (regeneration >= 0) {
            sink.potion(player, regeneration, level, 20 * (level + 2));
        }
        cloud(sink, player);
        if (heroic) {
            sink.guidedFlight(player, level * 20, 0.2 + level * 0.03);
        }
        sink.flush();

        int endingBurst = 20 * (level + 2) + 5;
        Scheduling.onEntityLater(player, endingBurst, () -> {
            if (!player.isOnline()) {
                return;
            }
            SinkReadback ending = sinks.create(env);
            cloud(ending, player);
            ending.flush();
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player
                && noFall.remove(player.getUniqueId()) != null) {
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        noFall.remove(event.getPlayer().getUniqueId());
        long now = nowTicks.getAsLong();
        lastActivation.entrySet().removeIf(entry -> now - entry.getValue() > NORMAL_COOLDOWN);
    }

    private static boolean blockedWorld(Player player) {
        return player.getWorld().getEnvironment() == World.Environment.THE_END
                || player.hasMetadata("inDungeonParkour")
                || player.getWorld().getName().equals("world_koth");
    }

    private void cloud(SinkReadback sink, Player player) {
        if (cloud < 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        sink.particle(player.getLocation(), cloud, 69, -1,
                random.nextDouble() * 2.0, random.nextDouble() * 2.0,
                random.nextDouble() * 2.0, 1.25);
    }
}
