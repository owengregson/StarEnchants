package tester.suite;

import engine.sink.ParticleDefaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.harness.Harness;

/**
 * Required particle data, live — the registry's data demands MOVE between versions ({@code ENTITY_EFFECT}
 * gained a required {@code Color} at 1.20.5; the 1.21.9 line made {@code EFFECT}/{@code INSTANT_EFFECT} take
 * {@code Particle.Spell}), and a miss is an {@code IllegalArgumentException} thrown into every dispatch flush.
 * Sweeps THIS version's whole {@code Particle} registry: every required data type must default through
 * {@link ParticleDefaults} (or be a known destination-shaped exception), and every defaulted particle must
 * actually spawn. A new version demanding a data class the defaulting can't build fails the matrix here,
 * not a production server's console.
 */
public final class ParticleSuite implements Harness.Scenario {

    /** Data types with no sensible default — they need a real destination, so the sink skips their bursts. */
    private static final Set<String> UNDEFAULTABLE = Set.of("Vibration", "Trail");

    private final Plugin plugin;

    public ParticleSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("particle.requiredDataDefaults");
        h.expect("particle.defaultedSpawnsClean");

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        Scheduling.onRegion(at, () -> {
            h.guard("particle.requiredDataDefaults", () -> {
                List<String> missing = new ArrayList<>();
                for (Particle particle : Particle.values()) {
                    Class<?> dataType = particle.getDataType();
                    if (dataType == Void.class || UNDEFAULTABLE.contains(dataType.getSimpleName())) {
                        continue;
                    }
                    if (ParticleDefaults.dataFor(particle) == null) {
                        missing.add(particle.name() + " needs " + dataType.getName());
                    }
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("required data with no default on this version: " + missing);
                }
            });
            h.guard("particle.defaultedSpawnsClean", () -> {
                for (Particle particle : Particle.values()) {
                    Object data = ParticleDefaults.dataFor(particle);
                    if (data != null) {
                        world.spawnParticle(particle, at, 1, 0.0, 0.0, 0.0, 0.0, data);
                    } else if (particle.getDataType() == Void.class) {
                        world.spawnParticle(particle, at, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            });
        });
    }
}
