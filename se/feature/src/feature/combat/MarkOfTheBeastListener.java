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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended Ghost-set Mark of the Beast implementation. */
public final class MarkOfTheBeastListener implements Listener {

    private static final String ENCHANT = "enchants/mark-of-the-beast";
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final int witchMagic;
    private final Map<UUID, Mark> marked = new ConcurrentHashMap<>();

    public MarkOfTheBeastListener(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        Objects.requireNonNull(resolvers, "resolvers");
        witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDefense(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerForCosmic(event.getDamager());
        if (!(event.getEntity() instanceof Player wearer) || attacker == null
                || !CosmicTierGate.tierSixPlusEnabled(wearer)
                || !CosmicDefenseGate.sourceCombatCause(event.getCause())
                || CosmicDefenseGate.silenced(wearer, env)
                || CombatDispatch.friendly(wearer, attacker)) {
            return;
        }
        int level = EnchantLevels.worn(wearer, ENCHANT);
        if (level <= 0 || level > 6 || ThreadLocalRandom.current().nextDouble() > level * 0.015) {
            return;
        }
        long now = env.nowTicks().getAsLong();
        Mark current = marked.get(attacker.getUniqueId());
        if (current != null && current.expiresAt > now) {
            return;
        }
        int seconds = level / 3 + 2;
        Mark next = new Mark(now + seconds * 20L);
        marked.put(attacker.getUniqueId(), next);

        SinkReadback sink = sinks.create(env);
        // The source rendered this at the defender; the advertised mark is on the attacker, so put it there.
        if (witchMagic >= 0) {
            sink.particle(attacker.getEyeLocation(), witchMagic, 20, -1, 0.6, 0.6, 0.6, 0.0);
        }
        double radius = 20 + level * 2;
        for (Entity nearby : attacker.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player viewer) {
                sink.message(viewer, "&e&l* MARK OF THE BEAST [&7" + attacker.getName() + ": "
                        + format(seconds) + "s&e&l] *");
            }
        }
        sink.message(attacker, "&c&l* MARK OF THE BEAST [&7" + format(seconds) + "s&c&l] *");
        sink.flush();

        Scheduling.onEntityLater(attacker, seconds * 20L, () -> {
            if (marked.remove(attacker.getUniqueId(), next) && attacker.isOnline()) {
                SinkReadback expiry = sinks.create(env);
                expiry.message(attacker, "&c&l* MARK OFF &c&l*");
                expiry.flush();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMarkedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Mark mark = marked.get(player.getUniqueId());
        long now = env.nowTicks().getAsLong();
        if (mark == null || mark.expiresAt <= now) {
            if (mark != null) {
                marked.remove(player.getUniqueId(), mark);
            }
            return;
        }
        event.setDamage(event.getDamage() * 2.0);
        double actual = event.getFinalDamage();
        if (actual > 0.01) {
            SinkReadback sink = sinks.create(env);
            sink.message(player, "&c* MARK OF THE BEAST [-&c" + format(actual) + "&c HP] *");
            sink.flush();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        marked.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        marked.clear();
    }

    static Player resolvePlayerForCosmic(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static String format(double value) {
        return new DecimalFormat("#.#").format(value);
    }

    private record Mark(long expiresAt) {
    }
}
