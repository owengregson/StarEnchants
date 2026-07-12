package engine.sink;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

/**
 * Default data for particles whose {@link Particle#getDataType()} is REQUIRED — a moving target across the
 * range: {@code DUST} always demanded {@code DustOptions}, {@code ENTITY_EFFECT} gained a required
 * {@code Color} at 1.20.5, and the 1.21.9 line made {@code EFFECT}/{@code INSTANT_EFFECT} take
 * {@code Particle.Spell} and {@code DRAGON_BREATH} a power float. Content authors the particle NAME only, so
 * the sink supplies each version's demanded data here instead of throwing {@code IllegalArgumentException}
 * into every dispatch flush. Data classes the floor API can't name (Spell) are covered by their conventional
 * {@code (Color, float)} constructor, found reflectively on the data type itself — new colour+power data
 * classes on future versions default the same way without a code change.
 *
 * <p>Defaults are the plain white/neutral rendering of each particle. A data type with no sensible default
 * (a {@code Vibration} needs a destination) resolves to {@code null}: the caller skips the spawn, and this
 * class warns ONCE per type — a visual silently missing should be author-visible, not flush spam.
 * Instances are immutable, so each resolved default is cached and shared.
 *
 * <p>Public for the live {@code ParticleSuite} pin, which asserts every required data type on the running
 * version either defaults here or is a known-undefaultable — new-version drift fails the matrix, not prod.
 */
public final class ParticleDefaults {

    private static final Logger LOG = System.getLogger("StarEnchants.Sink");

    /** Resolved default per data type; {@code Optional.empty()} = undefaultable (already warned). */
    private static final Map<Class<?>, Optional<Object>> CACHE = new ConcurrentHashMap<>();

    private ParticleDefaults() {
    }

    /** The default data instance for {@code particle}, or {@code null} when none exists ({@code Void} included). */
    public static Object dataFor(Particle particle) {
        Class<?> dataType = particle.getDataType();
        if (dataType == Void.class) {
            return null;
        }
        return CACHE.computeIfAbsent(dataType, type -> build(particle.name(), type)).orElse(null);
    }

    /** Cache-free default construction — the unit-testable core ({@code context} names the particle in the warn). */
    @SuppressWarnings("deprecation") // MaterialData: the floor's LEGACY_* particles demand the pre-1.13 block data
    static Optional<Object> build(String context, Class<?> dataType) {
        try {
            if (dataType == Particle.DustOptions.class) {
                return Optional.of(new Particle.DustOptions(Color.WHITE, 1.0f));
            }
            if (dataType == Particle.DustTransition.class) {
                return Optional.of(new Particle.DustTransition(Color.WHITE, Color.WHITE, 1.0f));
            }
            if (dataType == Color.class) {
                return Optional.of(Color.WHITE);
            }
            if (dataType == Float.class) {
                return Optional.of(1.0f); // DRAGON_BREATH power / SCULK_CHARGE roll — 1.0 renders visibly
            }
            if (dataType == Integer.class) {
                return Optional.of(0); // SHRIEK delay
            }
            if (dataType == BlockData.class) {
                return Optional.of(Material.STONE.createBlockData());
            }
            if (dataType == MaterialData.class) {
                // The 1.17.1 floor's LEGACY_BLOCK_CRACK/_DUST/_FALLING_DUST trio.
                return Optional.of(new MaterialData(Material.STONE));
            }
            if (dataType == ItemStack.class) {
                return Optional.of(new ItemStack(Material.STONE));
            }
            // A colour+power data class the floor API can't name (Particle.Spell, 1.21.9+ — and successors).
            return Optional.of(dataType.getConstructor(Color.class, float.class).newInstance(Color.WHITE, 1.0f));
        } catch (Throwable undefaultable) {
            // Cosmetic-only: never let a data quirk reach the flush. Once per type, not per burst.
            LOG.log(Level.WARNING, "particle " + context + " requires " + dataType.getName()
                    + " data with no default on this version - skipping its bursts", undefaultable);
            return Optional.empty();
        }
    }
}
