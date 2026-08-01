package feature.fx;

import compile.load.ParticleSpec;
import engine.sink.ParticleDefaults;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Modern impl of {@link ParticleFx} — the era-exclusive {@code overlay/modern} particle feedback (ADR-0044; §4).
 * Spawns via the injected cross-version resolver (token → live {@code Particle}); an unresolved token is skipped.
 * Particles whose data is REQUIRED on the running version spawn with {@link ParticleDefaults}' neutral data —
 * the same no-throw rule as the sink's bursts.
 */
public final class ModernParticleFx implements ParticleFx {

    private final Function<String, Particle> resolver;

    public ModernParticleFx(Function<String, Particle> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public void spawn(Player player, List<String> tokens, int count) {
        if (player == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            Particle particle = resolver.apply(token);
            if (particle != null) {
                // extra 1.0 = what the old 3-arg overload delegated to; keeps dataless tokens wire-identical
                spawnPlain(player, player.getLocation(), particle, Math.max(1, count), 0.0, 1.0);
            }
        }
    }

    /**
     * Spawn a configured {@link ParticleSpec} — count + spread + y-offset, and (for the dust/redstone particle)
     * an RGB colour via {@code DustOptions}, built cross-version off the resolved particle's data type so the
     * {@code REDSTONE→DUST} rename is transparent. A non-dust particle ignores the colour.
     */
    @Override
    public void spawn(Player player, ParticleSpec spec) {
        if (player == null || spec == null || spec.isEmpty()) {
            return;
        }
        Particle particle = resolver.apply(spec.type());
        if (particle == null) {
            return;
        }
        Location at = player.getLocation().add(0.0, spec.yOffset(), 0.0);
        double s = spec.spread();
        if (particle.getDataType() == Particle.DustOptions.class) {
            Particle.DustOptions dust = new Particle.DustOptions(
                    Color.fromRGB(spec.colorR(), spec.colorG(), spec.colorB()), 1.0f);
            player.getWorld().spawnParticle(particle, at, spec.amount(), s, s, s, spec.speed(), dust);
        } else {
            spawnPlain(player, at, particle, spec.amount(), s, spec.speed());
        }
    }

    /** A plain burst with the version's required data defaulted; an undefaultable requirement skips, never throws. */
    private static void spawnPlain(Player player, Location at, Particle particle, int count, double spread,
                                   double extra) {
        Object data = ParticleDefaults.dataFor(particle);
        if (data != null) {
            player.getWorld().spawnParticle(particle, at, count, spread, spread, spread, extra, data);
        } else if (particle.getDataType() == Void.class) {
            player.getWorld().spawnParticle(particle, at, count, spread, spread, spread, extra);
        }
    }
}
