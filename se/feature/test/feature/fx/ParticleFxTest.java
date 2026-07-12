package feature.fx;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** {@link ParticleFx}: resolves each token, spawns the resolvable ones, skips the unresolved without crashing. */
class ParticleFxTest {

    @Test
    void spawnsEachResolvableTokenAndSkipsTheRest() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location loc = mock(Location.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(loc);

        Map<String, Particle> table = Map.of("FLAME", Particle.FLAME, "SOUL", Particle.SOUL);
        ParticleFx fx = new ModernParticleFx(table::get); // returns null for an unknown token

        fx.spawn(player, List.of("FLAME", "NONSENSE_TOKEN", "SOUL"), 3);

        // Dataless tokens spawn through the explicit overload the old 3-arg call delegated to: spread 0, extra 1.
        verify(world).spawnParticle(Particle.FLAME, loc, 3, 0.0, 0.0, 0.0, 1.0);
        verify(world).spawnParticle(Particle.SOUL, loc, 3, 0.0, 0.0, 0.0, 1.0);
        verify(world, never()).spawnParticle(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
        verify(world, times(2)).spawnParticle(org.mockito.ArgumentMatchers.any(Particle.class),
                org.mockito.ArgumentMatchers.eq(loc), org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void emptyOrNullTokenListIsANoOp() {
        Player player = mock(Player.class);
        ParticleFx fx = new ModernParticleFx(t -> Particle.FLAME);
        fx.spawn(player, List.of(), 1);
        fx.spawn(player, null, 1);
        verify(player, never()).getWorld(); // never even touches the world
    }

    @Test
    void noneFxNeverSpawns() {
        Player player = mock(Player.class);
        ParticleFx.NONE.spawn(player, List.of("FLAME"), 1);
        verify(player, never()).getWorld();
    }
}
