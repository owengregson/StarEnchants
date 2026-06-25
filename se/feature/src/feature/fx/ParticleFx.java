package feature.fx;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Resolves configured particle tokens and spawns them at a player (§D soul-gem, §I dust feedback). Tokens
 * go through the injected cross-version resolver, so legacy names like {@code VILLAGER_HAPPY} resolve on
 * every version; an unresolved token is skipped, never a crash. Spawning touches the player's world, so
 * call on that player's own region thread (Folia).
 */
public final class ParticleFx {

    /** No-op fx — the default for unit/synthetic contexts. */
    public static final ParticleFx NONE = new ParticleFx(token -> null);

    private final Function<String, Particle> resolver;

    public ParticleFx(Function<String, Particle> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Spawn each resolvable token at {@code player}'s location ({@code count} particles each). */
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
