package feature.combat;

import engine.sink.PetSummons;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import engine.sink.SummonFlags;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import platform.resolve.RegistryResolvers;

/** Exact explosion and no-loot payload for creepers spawned by Cosmic Plague Carrier. */
public final class PlagueCarrierListener implements Listener {

    private final SinkFactory sinks;
    private final SinkEnv env;
    private final int poison;
    private final int blindness;
    private final int weakness;
    private final int slowness;
    private final int explosion;
    private final int explodeSound;

    public PlagueCarrierListener(SinkFactory sinks, SinkEnv env, RegistryResolvers resolvers) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        Objects.requireNonNull(resolvers, "resolvers");
        poison = resolvers.potionEffect("POISON").orElse(-1);
        blindness = resolvers.potionEffect("BLINDNESS").orElse(-1);
        weakness = resolvers.potionEffect("WEAKNESS").orElse(-1);
        slowness = resolvers.potionEffect("SLOW").orElse(-1);
        explosion = resolvers.particle("EXPLOSION_LARGE").orElse(-1);
        explodeSound = resolvers.sound("EXPLODE").orElse(-1);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        Entity carrier = event.getEntity();
        if (carrier == null) {
            return;
        }
        SummonFlags flags = PetSummons.flags(carrier.getUniqueId());
        if (flags == null || flags.plagueLevel() <= 0) {
            return;
        }

        int level = flags.plagueLevel();
        event.setCancelled(true);
        event.setYield(0.0f);
        Location at = event.getLocation();
        SinkReadback sink = sinks.create(env);
        if (explodeSound >= 0) {
            sink.sound(at, explodeSound, 1.0f, 1.0f);
        }
        if (explosion >= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            sink.particle(at, explosion, 3, -1,
                    random.nextDouble(), random.nextDouble(), random.nextDouble(), 0.5);
        }

        int amplifier = level == 8 ? 2 : level >= 4 ? 1 : 0;
        int duration = level * 40;
        for (Entity nearby : carrier.getNearbyEntities(5.0, 4.0, 5.0)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            if (poison >= 0) {
                sink.potion(living, poison, amplifier, duration);
            }
            if (level >= 3 && blindness >= 0) {
                sink.potion(living, blindness, amplifier, duration);
            }
            if (level >= 6 && weakness >= 0) {
                sink.potion(living, weakness, amplifier, duration);
            }
            if (level == 8 && slowness >= 0) {
                sink.potion(living, slowness, amplifier, duration);
            }
        }
        PetSummons.forget(carrier.getUniqueId());
        carrier.remove();
        sink.flush();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity carrier = event.getEntity();
        SummonFlags flags = PetSummons.flags(carrier.getUniqueId());
        if (flags == null || flags.plagueLevel() <= 0) {
            return;
        }
        event.setDroppedExp(0);
        event.getDrops().clear();
        PetSummons.forget(carrier.getUniqueId());
        carrier.remove();
    }
}
