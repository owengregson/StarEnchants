package feature.pet;

import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.combat.CombatDispatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;

/** Bug-fixed native implementation of the Cosmic World Destroyer pet's Dimensional Cage. */
public final class CosmicWorldDestroyer {

    static final int CAGE_TICKS = 100;
    private static volatile CosmicWorldDestroyer active;

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final int ironBars;
    private final int obsidian;
    private final int blindness;
    private final int poison;
    private final int wither;
    private final int anvilLand;
    private final int witherShoot;

    public CosmicWorldDestroyer(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        Objects.requireNonNull(resolvers, "resolvers");
        // Cosmic attempted to place the item-only IRON_BARDING as a block. IRON_BARS is the intended cage wall.
        ironBars = resolvers.material("IRON_BARS").orElse(-1);
        obsidian = resolvers.material("OBSIDIAN").orElse(-1);
        blindness = resolvers.potionEffect("BLINDNESS").orElse(-1);
        poison = resolvers.potionEffect("POISON").orElse(-1);
        wither = resolvers.potionEffect("WITHER").orElse(-1);
        anvilLand = resolvers.sound("ANVIL_LAND").orElse(-1);
        witherShoot = resolvers.sound("WITHER_SHOOT").orElse(-1);
        active = this;
    }

    public static boolean hasTargets(Player caster) {
        CosmicWorldDestroyer service = active;
        return service != null && !service.targets(caster).isEmpty();
    }

    public static boolean activate(Player caster) {
        CosmicWorldDestroyer service = active;
        if (service == null) {
            return false;
        }
        List<Player> targets = service.targets(caster);
        if (targets.isEmpty()) {
            return false;
        }
        SinkReadback opening = service.sinks.create(service.env);
        if (service.witherShoot >= 0) {
            opening.privateSound(caster, service.witherShoot, 10.0f, 2.0f);
        }
        for (Player target : targets) {
            service.cage(opening, target);
            if (service.blindness >= 0) {
                opening.potion(target, service.blindness, 100, 200);
            }
            if (service.poison >= 0) {
                opening.potion(target, service.poison, 18, 100);
            }
            if (service.wither >= 0) {
                opening.potion(target, service.wither, 18, 100);
            }
            opening.message(target, "&5&l** DIMENSIONAL CAGE &7[&c3s&7]&5&l **");
            if (service.anvilLand >= 0) {
                opening.privateSound(target, service.anvilLand, 2.0f, 2.0f);
            }
        }
        opening.flush();
        for (Player target : targets) {
            service.damagePulse(target, 11);
        }
        return true;
    }

    private List<Player> targets(Player caster) {
        List<Player> out = new ArrayList<>();
        for (Entity entity : caster.getNearbyEntities(30.0, 30.0, 30.0)) {
            if (entity instanceof Player target && !target.equals(caster)
                    && !CombatDispatch.friendly(caster, target)) {
                out.add(target);
            }
        }
        return out;
    }

    private void cage(SinkReadback sink, Player target) {
        if (ironBars < 0 || obsidian < 0) {
            return;
        }
        Location at = target.getLocation();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sink.tempBlock(new Location(at.getWorld(), x + dx, y - 1, z + dz), obsidian,
                        CAGE_TICKS, 2, false, target.getUniqueId());
                sink.tempBlock(new Location(at.getWorld(), x + dx, y + 3, z + dz), obsidian,
                        CAGE_TICKS, 2, false, target.getUniqueId());
                if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
                    for (int dy = 0; dy < 3; dy++) {
                        sink.tempBlock(new Location(at.getWorld(), x + dx, y + dy, z + dz), ironBars,
                                CAGE_TICKS, 2, false, target.getUniqueId());
                    }
                }
            }
        }
        sink.tempBlock(new Location(at.getWorld(), x, y, z), ironBars,
                CAGE_TICKS, 2, false, target.getUniqueId());
        sink.tempBlock(new Location(at.getWorld(), x, y + 1, z), ironBars,
                CAGE_TICKS, 2, false, target.getUniqueId());
    }

    private void damagePulse(Player target, int remaining) {
        if (remaining <= 0 || !target.isOnline() || target.isDead()) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        sink.damage(target, ThreadLocalRandom.current().nextInt(5, 10));
        sink.flush();
        if (remaining > 1) {
            Scheduling.onEntityLater(target, 5L, () -> damagePulse(target, remaining - 1));
        }
    }

    public void stop() {
        if (active == this) {
            active = null;
        }
    }
}
