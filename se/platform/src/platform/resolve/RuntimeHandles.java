package platform.resolve;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import schema.spec.HandleCategory;

/**
 * The runtime side of cross-version resolution (docs/architecture.md §9): turns the interned handle
 * ids the compiled effects carry back into the live Bukkit objects the {@code Sink} dispatcher
 * mutates the world with — the inverse of {@link RenameResolvers}, completing the round-trip
 * <em>token &rarr; id (compile) &rarr; live object (runtime)</em>. The id&rarr;name step uses the
 * same resolver instance that interned the ids (so the names are exactly the ones it resolved for
 * this server); the name&rarr;object step reuses {@link RegistrySupport}'s version-adaptive lookup.
 *
 * <p>Bound to one resolver instance for its lifetime and caches id&rarr;object so the version-volatile
 * reflective lookup happens at most once per handle, never on the combat hot path. Concurrent —
 * combat reads it from many region threads on Folia. A handle that does not resolve on this version
 * returns {@code null}, and the caller skips that one op (warn-and-skip, never a crash).
 */
public final class RuntimeHandles {

    private final RenameResolvers resolvers;
    private final Map<HandleCategory, Map<Integer, Object>> cache = new EnumMap<>(HandleCategory.class);

    public RuntimeHandles(RenameResolvers resolvers) {
        this.resolvers = resolvers;
        for (HandleCategory category : HandleCategory.values()) {
            cache.put(category, new ConcurrentHashMap<>());
        }
    }

    /** The live object for an interned handle id in {@code category}, or {@code null} if unresolved. */
    public Object resolve(HandleCategory category, int id) {
        Map<Integer, Object> categoryCache = cache.get(category);
        Object cached = categoryCache.get(id);
        if (cached != null) {
            return cached;
        }
        String name = resolvers.nameOf(category, id);
        if (name == null) {
            return null;
        }
        Object object = RegistrySupport.lookup(category, name);
        if (object != null) {
            categoryCache.put(id, object);
        }
        return object;
    }

    public Material material(int id) {
        return (Material) resolve(HandleCategory.MATERIAL, id);
    }

    public Enchantment enchantment(int id) {
        return (Enchantment) resolve(HandleCategory.ENCHANTMENT, id);
    }

    public PotionEffectType potionEffect(int id) {
        return (PotionEffectType) resolve(HandleCategory.POTION_EFFECT, id);
    }

    public Particle particle(int id) {
        return (Particle) resolve(HandleCategory.PARTICLE, id);
    }

    public EntityType entityType(int id) {
        return (EntityType) resolve(HandleCategory.ENTITY_TYPE, id);
    }

    public Attribute attribute(int id) {
        return (Attribute) resolve(HandleCategory.ATTRIBUTE, id);
    }

    public Sound sound(int id) {
        return (Sound) resolve(HandleCategory.SOUND, id);
    }
}
