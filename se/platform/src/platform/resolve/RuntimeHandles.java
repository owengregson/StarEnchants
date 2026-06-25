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
 * The runtime side of cross-version resolution (docs/architecture.md §9): turns interned handle ids back
 * into live Bukkit objects, completing the round-trip <em>token &rarr; id (compile) &rarr; object (runtime)</em>.
 * The id&rarr;name step uses the same resolver that interned the ids. Caches id&rarr;object so the volatile
 * reflective lookup happens at most once per handle, off the hot path. Concurrent — Folia reads it from many
 * region threads. An unresolved handle returns {@code null} and the caller warn-and-skips, never crashes.
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

    /**
     * The live object a canonical {@code name} denotes, or {@code null}. Name-keyed companion to
     * {@link #resolve(HandleCategory, int)} for referents the dispatcher needs by well-known name (e.g.
     * the implicit max-health attribute behind {@code addMaxHealth}). Not cached: cold path only.
     */
    public Object resolveByName(HandleCategory category, String name) {
        return RegistrySupport.lookup(category, name);
    }

    /** The live object for an interned handle id, or {@code null} if unresolved. */
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
