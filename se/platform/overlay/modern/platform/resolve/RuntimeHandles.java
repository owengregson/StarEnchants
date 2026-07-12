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
    private final ModernHandleLookup lookup = new ModernHandleLookup(); // modern-only: resolve names directly
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
     * the implicit max-health attribute behind {@code addMaxHealth}). Walks the SAME {@link Aliases} chain
     * the compile resolve does, both directions — a well-known name written for one era must keep resolving
     * after a registry rename (1.21.3+ dropped the GENERIC_ attribute prefixes: the 1.8.1 nature-crystal
     * regression, caught live by MaxHealthSuite). Not cached: cold path only.
     */
    public Object resolveByName(HandleCategory category, String name) {
        Object direct = lookup.lookup(category, name);
        if (direct != null) {
            return direct;
        }
        Map<String, String> aliases = Aliases.forCategory(category);
        String canonical = Aliases.normalize(name);
        String forward = aliases.get(canonical);                 // legacy spelling → modern name
        if (forward != null) {
            Object object = lookup.lookup(category, forward);
            if (object != null) {
                return object;
            }
        }
        for (Map.Entry<String, String> alias : aliases.entrySet()) {   // modern spelling → legacy name
            if (alias.getValue().equals(canonical)) {
                Object object = lookup.lookup(category, alias.getKey());
                if (object != null) {
                    return object;
                }
            }
        }
        return null;
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
        Object object = lookup.lookup(category, name);
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
