package platform.resolve;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import schema.spec.HandleCategory;

/**
 * The live, cross-version handle lookup behind {@link RegistryResolvers} and {@code RuntimeHandles}
 * (docs/architecture.md §9; cross-version-item-api skill). For a given category and an already-
 * alias-resolved canonical name, {@link #lookup} returns the live Bukkit object (or {@code null}),
 * spanning 1.17.1 &rarr; 26.1.x where the relevant API shifted under us: enums became registry-backed
 * interfaces ({@code Enchantment}/{@code PotionEffectType} at the 1.20.5 flip; {@code Attribute}/
 * {@code Sound} at 1.21.3), the attribute {@code generic.} key prefix was dropped, and the per-version
 * {@code Registry} constants differ (no potion-effect registry on the 1.17.1 floor).
 *
 * <p>Existence ({@link #exists}) is simply {@code lookup != null} — one body, used both at compile
 * time (does this token resolve → intern it) and at runtime (give me the object to mutate the world
 * with). Strategy: hard-reference only surfaces stable across the whole range ({@link Registry} and
 * {@link NamespacedKey} themselves, {@link Material#getMaterial}, {@link Enchantment#getByKey}); for
 * everything volatile, look up the {@code Registry} constant by reflection (an absent field is just
 * "not on this version") and fall back to a reflective enum {@code valueOf} (so a type that has since
 * become an interface is never hard-linked). Every probe is wrapped so a missing field/method or a
 * bad key degrades to {@code null} — never a crash. Lookup runs at load time and is cached by callers,
 * so the reflection cost never touches the hot path.
 */
final class RegistrySupport {

    private RegistrySupport() {
    }

    /** Whether {@code canonicalName} (upper-case Bukkit style) resolves for {@code category} here. */
    static boolean exists(HandleCategory category, String canonicalName) {
        return lookup(category, canonicalName) != null;
    }

    /** The live Bukkit object {@code canonicalName} denotes for {@code category}, or {@code null}. */
    static Object lookup(HandleCategory category, String canonicalName) {
        try {
            return switch (category) {
                case MATERIAL -> Material.getMaterial(canonicalName);
                case ENCHANTMENT -> Enchantment.getByKey(minecraft(key(canonicalName)));
                case POTION_EFFECT -> firstNonNull(
                        registryLookup("EFFECT", key(canonicalName)),
                        registryLookup("MOB_EFFECT", key(canonicalName)),
                        legacyPotionEffect(canonicalName));
                case ENTITY_TYPE -> firstNonNull(
                        registryLookup("ENTITY_TYPE", key(canonicalName)),
                        enumValueOf("org.bukkit.entity.EntityType", canonicalName));
                case ATTRIBUTE -> firstNonNull(
                        registryLookup("ATTRIBUTE", key(canonicalName)),
                        registryLookup("ATTRIBUTE", "generic." + key(canonicalName)),
                        enumValueOf("org.bukkit.attribute.Attribute", canonicalName));
                case PARTICLE -> firstNonNull(
                        registryLookup("PARTICLE_TYPE", key(canonicalName)),
                        enumValueOf("org.bukkit.Particle", canonicalName));
                case SOUND -> firstNonNull(
                        enumValueOf("org.bukkit.Sound", canonicalName),  // enum era (≤1.21.2)
                        staticField("org.bukkit.Sound", canonicalName),  // interface-with-constants era (1.21.3+/26.1.x)
                        // Last-ditch registry lookup. NOTE: this naive '_'→'.' is WRONG for names with
                        // multi-word segments (entity.lightning_bolt.thunder), so it only catches the
                        // single-segment cases the static-field path missed; the field path is the real fix.
                        registryLookup("SOUNDS", key(canonicalName).replace('_', '.')));
            };
        } catch (Throwable failedProbe) {
            // Any reflective/linkage failure means "not resolvable here" — caller warn-skips.
            return null;
        }
    }

    private static Object firstNonNull(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** The minecraft-namespace registry key for a canonical name (lower-case). */
    private static String key(String canonicalName) {
        return canonicalName.toLowerCase(Locale.ROOT);
    }

    private static NamespacedKey minecraft(String key) {
        return NamespacedKey.minecraft(key);
    }

    /**
     * Reflectively read {@code Registry.<field>} and look the key up, returning the object or
     * {@code null}. The field is read by reflection because which {@code Registry} constants exist
     * varies by version; the {@link Registry}/{@link NamespacedKey} cast and {@code get(NamespacedKey)}
     * call are safe because those are stable across the range.
     */
    private static Object registryLookup(String field, String key) {
        try {
            Field f = Registry.class.getField(field);
            Object value = f.get(null);
            if (!(value instanceof Registry<?> registry)) {
                return null;
            }
            return registry.get(minecraft(key));
        } catch (Throwable absent) {
            return null;
        }
    }

    /** Reflective {@code EnumType.valueOf(name)} so a now-interface type is never hard-linked. */
    private static Object enumValueOf(String className, String name) {
        try {
            Class<?> type = Class.forName(className);
            if (!type.isEnum()) {
                return null;
            }
            Method valueOf = type.getMethod("valueOf", String.class);
            return valueOf.invoke(null, name);
        } catch (Throwable notFound) {
            return null; // unknown name (InvocationTargetException) or not an enum here
        }
    }

    /**
     * Reflective public static field by name — the cross-era resolution for a type that was an enum
     * and became a registry-backed <em>interface</em> with the same named constants (Bukkit's
     * {@code Sound} at 1.21.3+). It correctly resolves a name with multi-word segments
     * ({@code ENTITY_LIGHTNING_BOLT_THUNDER}) where a naive {@code '_'}&rarr;{@code '.'} registry key
     * would mangle the boundary, because the interface itself holds the constant under its enum-style
     * name. Returns {@code null} if there is no such static field on this version.
     */
    private static Object staticField(String className, String name) {
        try {
            java.lang.reflect.Field field = Class.forName(className).getField(name);
            return java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? field.get(null) : null;
        } catch (Throwable notFound) {
            return null;
        }
    }

    /** The floor path for potion effects, which have no Registry constant before ~1.20.2. */
    private static Object legacyPotionEffect(String name) {
        try {
            return PotionEffectType.getByName(name);
        } catch (Throwable removed) {
            return null;
        }
    }
}
