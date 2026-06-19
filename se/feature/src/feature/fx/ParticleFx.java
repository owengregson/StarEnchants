package feature.fx;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Resolves configured particle tokens to live {@link Particle}s and spawns them at a player (the §D soul-gem
 * and §I dust feedback). Tokens go through the injected cross-version resolver (the alias-aware
 * {@code RegistryResolvers#particle} → {@code RuntimeHandles#particle} path), so legacy names like
 * {@code VILLAGER_HAPPY} resolve on every version; an unresolved token is skipped, never a crash. Multiple
 * tokens per knob are supported (§D "multiple particles-…"). The spawn touches the player's world, so call
 * it on that player's own region thread (the soul toggle, the dust click, and the aura's per-entity hop all
 * run there). {@link #NONE} is a no-op for tests/synthetic contexts.
 */
public final class ParticleFx {

    /** A no-op fx (resolves nothing) — the default for unit/synthetic contexts. */
    public static final ParticleFx NONE = new ParticleFx(token -> null);

    private final Function<String, Particle> resolver;

    public ParticleFx(Function<String, Particle> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Spawn each resolvable token in {@code tokens} at {@code player}'s location ({@code count} particles each). */
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
