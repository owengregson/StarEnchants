package feature.pet;

import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.protect.ProtectionService;

/** Exact intended implementation of Cosmic's Smite inventory pet. */
public final class CosmicSmitePet {

    private static volatile CosmicSmitePet active;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;

    public CosmicSmitePet(SinkFactory sinks, SinkEnv env, ProtectionService protection) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        active = this;
    }

    public static boolean hasTarget(Player caster) {
        CosmicSmitePet service = active;
        return service != null && service.target(caster) != null;
    }

    public static boolean activate(Player caster, int level) {
        CosmicSmitePet service = active;
        if (service == null) {
            return false;
        }
        LivingEntity target = service.target(caster);
        if (target == null) {
            return false;
        }
        SinkReadback sink = service.sinks.create(service.env);
        sink.lightning(target, true, 0.0, caster);
        if (target instanceof Player player) {
            int ticks = 20 + level * 5;
            sink.movementSpeed(player, 0.0, ticks);
            sink.message(player, "&c&l(!) &cFrozen by " + caster.getName()
                    + "'s Smite Pet [" + freezeSeconds(level) + "s]!");
        }
        sink.flush();
        return true;
    }

    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    private static Block aimedBlock(Player caster) {
        // The same erased Bukkit method is Set<Byte> on 1.8 and Set<Material> on modern APIs.
        Block aimed = caster.getTargetBlock((java.util.Set) null, 8);
        return aimed;
    }

    private LivingEntity target(Player caster) {
        Block aimed = aimedBlock(caster);
        LivingEntity closest = null;
        double closestSquared = 4.0;
        for (Entity entity : caster.getNearbyEntities(8.0, 8.0, 8.0)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(aimed.getLocation());
            if (distanceSquared < 4.0 && distanceSquared < closestSquared) {
                if (living instanceof Player player
                        && !protection.allows(caster.getUniqueId(), player.getLocation())) {
                    continue;
                }
                closest = living;
                closestSquared = distanceSquared;
            }
        }
        return closest;
    }

    private static String freezeSeconds(int level) {
        double seconds = 1.0 + level * 0.25;
        return Double.toString(seconds);
    }

    public void stop() {
        if (active == this) {
            active = null;
        }
    }
}
