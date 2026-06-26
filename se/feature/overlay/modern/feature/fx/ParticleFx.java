package feature.fx;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Spawns configured particle tokens at a player (§D soul-gem, §I dust feedback) via the injected
 * cross-version resolver; an unresolved token is skipped, never a crash. Spawning touches the player's
 * world — call on that player's own region thread (Folia).
 */
public final class ParticleFx {

    /** No-op fx for unit/synthetic contexts. */
    public static final ParticleFx NONE = new ParticleFx(token -> null);

    private final Function<String, Particle> resolver;

    public ParticleFx(Function<String, Particle> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

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
                player.getWorld().spawnParticle(particle, player.getLocation(), Math.max(1, count));
            }
        }
    }
}
