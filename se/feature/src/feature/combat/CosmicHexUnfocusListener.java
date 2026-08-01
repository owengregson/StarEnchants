package feature.combat;

import engine.effect.kind.HeldEnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Exact intended stateful implementations of Cosmic Hex and Unfocus. */
public final class CosmicHexUnfocusListener implements Listener {

    private static final String HEX = "enchants/hex";
    private static final String UNFOCUS = "enchants/unfocus";
    private static final ThreadLocal<DecimalFormat> TWO = ThreadLocal.withInitial(() -> new DecimalFormat("#.##"));

    private record TimedLevel(int level, long until) {
    }

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final Map<UUID, TimedLevel> hexed = new ConcurrentHashMap<>();
    private final Map<UUID, TimedLevel> unfocused = new ConcurrentHashMap<>();
    private final int witchMagic;
    private final int portalTrigger;
    private final int arrowHit;

    public CosmicHexUnfocusListener(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        Objects.requireNonNull(resolvers, "resolvers");
        witchMagic = resolvers.particle("SPELL_WITCH").orElse(-1);
        portalTrigger = resolvers.sound("PORTAL_TRIGGER").orElse(-1);
        arrowHit = resolvers.sound("ARROW_HIT").orElse(-1);
    }

    /** Unfocus halves every projectile hit made by an affected player, even when the hit was cancelled. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUnfocusedProjectile(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        TimedLevel state = live(unfocused, shooter.getUniqueId());
        if (state == null) {
            return;
        }
        event.setDamage(event.getDamage() / 2.0);
        SinkReadback sink = sinks.create(env);
        sink.message(shooter, "&2** UNFOCUSED [50% BOW DMG] **");
        sink.flush();
    }

    /** Apply Unfocus by re-reading the current bow at impact, or from a direct bow-in-hand attack. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnfocusApply(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player attacker;
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else {
            return;
        }
        int level = unfocusLevelAtImpact(attacker);
        CosmicReflect.Route route = CosmicReflect.route(attacker, target, level, 4);
        Player source = route.source();
        Player affected = route.target();
        if (level <= 0 || live(unfocused, affected.getUniqueId()) != null
                || ThreadLocalRandom.current().nextDouble() >= 0.5 * level) {
            return;
        }
        int duration = level * 40;
        TimedLevel state = new TimedLevel(level, env.nowTicks().getAsLong() + duration);
        unfocused.put(affected.getUniqueId(), state);
        SinkReadback sink = sinks.create(env);
        sink.message(affected, "&2** UNFOCUS [" + level * 2 + "s] **");
        if (portalTrigger >= 0) {
            sink.privateSound(affected, portalTrigger, 1.2f, 0.6f);
        }
        if (arrowHit >= 0) {
            sink.privateSoundAt(source, affected.getLocation(), arrowHit, 1.2f, 2.5f);
        }
        sink.flush();
    }

    /** Hex application from a direct axe hit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHexApply(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        int level = HeldEnchantLevels.held(attacker, HEX);
        CosmicReflect.Route route = CosmicReflect.route(attacker, target, level, 5);
        Player affected = route.target();
        if (level <= 0 || live(hexed, affected.getUniqueId()) != null
                || ThreadLocalRandom.current().nextDouble() >= 0.02 * level) {
            return;
        }
        int seconds = 2 + level / 2;
        TimedLevel state = new TimedLevel(level, env.nowTicks().getAsLong() + seconds * 20L);
        hexed.put(affected.getUniqueId(), state);
        SinkReadback sink = sinks.create(env);
        if (witchMagic >= 0) {
            sink.particle(affected.getEyeLocation(), witchMagic, 20, -1, 0.0, 0.0, 0.0, 0.6);
        }
        sink.message(affected, "&5&l* HEX DEBUFF [&d&l" + seconds + "s&5&l] *");
        sink.flush();
        Scheduling.onEntityLater(affected, seconds * 20L, () -> {
            if (hexed.remove(affected.getUniqueId(), state) && affected.isOnline()) {
                SinkReadback off = sinks.create(env);
                off.message(affected, "&d&l* HEX OFF *");
                off.flush();
            }
        });
    }

    /** A hexed living melee damager takes generic self-damage capped by the Hex level. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHexedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager) || event.getFinalDamage() <= 0.0) {
            return;
        }
        TimedLevel state = live(hexed, damager.getUniqueId());
        if (state == null) {
            return;
        }
        double amount = Math.min(event.getFinalDamage(), 5 + Math.max(0, state.level() - 2));
        SinkReadback sink = sinks.create(env);
        sink.damage(damager, amount);
        if (damager instanceof Player player) {
            sink.message(player, "&5* HEX [&c" + TWO.get().format(amount) + "&5 DMG] *");
        }
        sink.flush();
    }

    static int unfocusLevelAtImpact(Player attacker) {
        return HeldEnchantLevels.held(attacker, UNFOCUS);
    }

    private TimedLevel live(Map<UUID, TimedLevel> map, UUID id) {
        TimedLevel state = map.get(id);
        if (state != null && state.until() <= env.nowTicks().getAsLong()) {
            map.remove(id, state);
            return null;
        }
        return state;
    }

    public void stop() {
        hexed.clear();
        unfocused.clear();
    }
}
