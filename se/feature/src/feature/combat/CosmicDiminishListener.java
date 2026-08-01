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
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Exact intended persistent-cap state for Cosmic Diminish and Vengeful Diminish. */
public final class CosmicDiminishListener implements Listener {

    private static final String DIMINISH = "enchants/diminish";
    private static final String VENGEFUL = "enchants/vengeful-diminish";

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final LongSupplier nowTicks;
    private final Map<UUID, Double> caps = new ConcurrentHashMap<>();
    private final ThreadLocal<DecimalFormat> format = ThreadLocal.withInitial(() -> new DecimalFormat("#.##"));

    public CosmicDiminishListener(SinkFactory sinks, SinkEnv env, LongSupplier nowTicks) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player wearer)
                || event.getFinalDamage() <= 0.0
                || env.stores().suppression().allSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())
                || env.stores().suppression().defenseSuppressed(wearer.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        int heroic = CosmicTierGate.tierSixPlusEnabled(wearer) ? EnchantLevels.worn(wearer, VENGEFUL) : 0;
        int normal = heroic > 0 ? 0 : EnchantLevels.worn(wearer, DIMINISH);
        if (heroic <= 0 && normal <= 0) {
            return;
        }

        UUID wearerId = wearer.getUniqueId();
        Double cap = caps.get(wearerId);
        double incomingFinal = event.getFinalDamage();
        if (cap != null && cap > 0.0 && incomingFinal > cap) {
            caps.remove(wearerId, cap);
            setFinalDamage(event, cap);
            if (heroic > 0) {
                Player attacker = attackingPlayer(event);
                if (attacker != null && !attacker.equals(wearer)) {
                    SinkReadback sink = sinks.create(env);
                    sink.damage(attacker, incomingFinal - cap, wearer);
                    sink.flush();
                }
            }
            return;
        }

        double chance = heroic > 0 ? 0.05 * heroic : 0.015 * normal;
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        if (heroic > 0) {
            // Source order is deliberate: halve the Bukkit base first, then derive the next cap from the
            // resulting final damage. The extra /2 therefore stores roughly one quarter of the original hit.
            event.setDamage(event.getDamage() / 2.0);
            double nextCap = event.getFinalDamage() / 2.0;
            caps.put(wearerId, nextCap);
            message(wearer, "&e&l* DIMINISH [&eMAX DMG: " + format.get().format(nextCap) + "&l] *");
        } else {
            double nextCap = Math.max(0.0, event.getFinalDamage());
            caps.put(wearerId, nextCap);
            message(wearer, "&e&l* DIMINISH [&eDMG: " + format.get().format(nextCap) + "&l] *");
        }
    }

    /**
     * Set the event base to the value whose recalculated final damage is the requested ceiling. Cosmic wrote the
     * post-armour ceiling back as base damage, applying armour twice; this binary search ships the advertised cap.
     */
    private static void setFinalDamage(EntityDamageEvent event, double wantedFinal) {
        double originalBase = event.getDamage();
        double originalFinal = event.getFinalDamage();
        if (wantedFinal <= 0.0 || originalBase <= 0.0 || originalFinal <= 0.0) {
            event.setDamage(0.0);
            return;
        }
        double low = 0.0;
        double high = originalBase;
        for (int i = 0; i < 24; i++) {
            double mid = (low + high) * 0.5;
            event.setDamage(mid);
            if (event.getFinalDamage() > wantedFinal) {
                high = mid;
            } else {
                low = mid;
            }
        }
        event.setDamage(low);
    }

    private void message(Player player, String text) {
        SinkReadback sink = sinks.create(env);
        sink.message(player, text);
        sink.flush();
    }

    private static Player attackingPlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        caps.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        caps.clear();
        format.remove();
    }
}
