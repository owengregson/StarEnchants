package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * {@link ParticleDefaults#build} — the server-free branches with hand-computed expectations. The BlockData
 * branch (needs a live registry) and the per-version required-data sweep are the live {@code ParticleSuite}'s
 * job; this pins the pure construction rules, including the reflective colour+power fallback that covers
 * {@code Particle.Spell} on APIs newer than the compile floor.
 */
class ParticleDefaultsTest {

    @Test
    void dustDefaultsToWhiteSizeOne() {
        Particle.DustOptions dust =
                (Particle.DustOptions) ParticleDefaults.build("DUST", Particle.DustOptions.class).orElseThrow();
        assertEquals(Color.WHITE, dust.getColor());
        assertEquals(1.0f, dust.getSize());
    }

    @Test
    void dustTransitionDefaultsToWhiteBothEnds() {
        Particle.DustTransition transition = (Particle.DustTransition) ParticleDefaults
                .build("DUST_COLOR_TRANSITION", Particle.DustTransition.class).orElseThrow();
        assertEquals(Color.WHITE, transition.getColor());
        assertEquals(Color.WHITE, transition.getToColor());
    }

    @Test
    void primitivesAndColourDefault() {
        assertEquals(Color.WHITE, ParticleDefaults.build("ENTITY_EFFECT", Color.class).orElseThrow());
        assertEquals(1.0f, ParticleDefaults.build("DRAGON_BREATH", Float.class).orElseThrow());
        assertEquals(0, ParticleDefaults.build("SHRIEK", Integer.class).orElseThrow());
    }

    @Test
    void itemDefaultsToStone() {
        ItemStack item = (ItemStack) ParticleDefaults.build("ITEM", ItemStack.class).orElseThrow();
        assertEquals(Material.STONE, item.getType());
    }

    /** The {@code Particle.Spell} shape: a data class the floor API can't name, with a (Color, float) ctor. */
    @Test
    void colourPowerClassDefaultsReflectively() {
        Object spell = ParticleDefaults.build("EFFECT", SpellShaped.class).orElseThrow();
        SpellShaped shaped = assertInstanceOf(SpellShaped.class, spell);
        assertEquals(Color.WHITE, shaped.color);
        assertEquals(1.0f, shaped.power);
    }

    @Test
    void undefaultableTypeResolvesEmpty() {
        Optional<Object> none = ParticleDefaults.build("VIBRATION", NoUsableCtor.class);
        assertTrue(none.isEmpty());
    }

    public static final class SpellShaped {
        final Color color;
        final float power;

        public SpellShaped(Color color, float power) {
            this.color = color;
            this.power = power;
        }
    }

    private static final class NoUsableCtor {
        private NoUsableCtor(String needsADestination) {
        }
    }
}
