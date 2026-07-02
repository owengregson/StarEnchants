package feature.fx;

import compile.load.ParticleSpec;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * The version edge for spawning configured particle feedback at a player (§D soul-gem, §I dust; ADR-0044;
 * docs/legacy-1.8.9-codeshare-design.md §4): 1.8.9 has no {@code org.bukkit.Particle}/{@code spawnParticle},
 * so particles cross as NMS packets there. Two era-exclusive impls back it — {@code ModernParticleFx}
 * (resolver-fed {@code spawnParticle}, {@code overlay/modern}) and {@code LegacyParticleFx} (NMS
 * {@code PacketPlayOutWorldParticles}, {@code overlay/legacy}) — injected into the fx consumers, which are blind
 * to the difference. Spawning touches the player's world — call on that player's own region thread (Folia).
 */
public interface ParticleFx {

    /** No-op fx for unit/synthetic contexts and era feeders with no particle backend. */
    ParticleFx NONE = new ParticleFx() {
        @Override
        public void spawn(Player player, List<String> tokens, int count) {
        }

        @Override
        public void spawn(Player player, ParticleSpec spec) {
        }
    };

    /** Spawn each resolved token {@code count} times at the player; an unresolved token is skipped, never a crash. */
    void spawn(Player player, List<String> tokens, int count);

    /** Spawn a configured {@link ParticleSpec} (count + spread + y-offset, and RGB for dust where supported). */
    void spawn(Player player, ParticleSpec spec);
}
