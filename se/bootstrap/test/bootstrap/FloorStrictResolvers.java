package bootstrap;

import compile.resolve.PlatformResolvers;
import java.util.OptionalInt;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import schema.spec.HandleCategory;

/**
 * The offline handle universe every shipped pack's compile gate resolves against: material/sound/particle/
 * entity/attribute tokens must exist in the floor ({@code 1.17.1}) Bukkit enums, resolved through the
 * production {@link HandleResolver} + {@link Aliases} exactly as the runtime resolves them. Floor enums are
 * the strictest universe (shipped content must run on the floor too) and are plain enums there, so resolution
 * needs no server. Registry-backed handles (potion effects, enchantments) can't be enumerated offline — they
 * stay permissive here and are owned by the live matrix.
 */
final class FloorStrictResolvers {

    static final PlatformResolvers INSTANCE = new PlatformResolvers() {
        @Override public OptionalInt material(String t) { return strict(HandleCategory.MATERIAL, t, n -> enumExists(Material.class, n)); }
        @Override public OptionalInt sound(String t) { return strict(HandleCategory.SOUND, t, n -> enumExists(Sound.class, n)); }
        @Override public OptionalInt particle(String t) { return strict(HandleCategory.PARTICLE, t, n -> enumExists(Particle.class, n)); }
        @Override public OptionalInt entityType(String t) { return strict(HandleCategory.ENTITY_TYPE, t, n -> enumExists(EntityType.class, n)); }
        @Override public OptionalInt attribute(String t) { return strict(HandleCategory.ATTRIBUTE, t, n -> enumExists(Attribute.class, n)); }
        @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
    };

    private FloorStrictResolvers() {
    }

    private static OptionalInt strict(HandleCategory category, String token, Predicate<String> exists) {
        return HandleResolver.resolve(token, Aliases.forCategory(category), exists).isPresent()
                ? OptionalInt.of(0)
                : OptionalInt.empty();
    }

    private static <E extends Enum<E>> boolean enumExists(Class<E> type, String name) {
        try {
            Enum.valueOf(type, name);
            return true;
        } catch (IllegalArgumentException notAConstant) {
            return false;
        }
    }
}
