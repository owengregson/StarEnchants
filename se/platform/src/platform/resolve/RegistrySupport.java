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
 * The live, cross-version existence checks behind {@link RegistryResolvers} (docs/architecture.md
 * §9; cross-version-item-api skill). For a given category and an already-alias-resolved canonical
 * name, it answers "does this resolve on THIS server" — spanning 1.17.1 &rarr; 26.1.x, where the
 * relevant API shifted under us: enums became registry-backed interfaces ({@code Enchantment},
 * {@code PotionEffectType} at the 1.20.5 flip; {@code Attribute}/{@code Sound} at 1.21.3), the
 * attribute {@code generic.} key prefix was dropped, and the per-version {@code Registry} constants
 * differ (there is no potion-effect registry on the 1.17.1 floor).
 *
 * <p>Strategy: hard-reference only surfaces stable across the whole range ({@link Registry} and
 * {@link NamespacedKey} themselves, {@link Material#getMaterial}, {@link Enchantment#getByKey}); for
 * everything volatile, look up the {@code Registry} constant by reflection (an absent field is just
 * "not on this version") and fall back to a reflective enum {@code valueOf} (so a type that has
 * since become an interface is never hard-linked). Every probe is wrapped so a missing field/method
 * or a bad key degrades to {@code false} — an unresolvable token is then warn-and-skipped by the
 * compiler, never a crash. Existence runs at compile/load time, never on the hot path, so the
 * reflection cost is irrelevant.
 */
final class RegistrySupport {

    private RegistrySupport() {
    }

    /** Whether {@code canonicalName} (upper-case Bukkit style) resolves for {@code category} here. */
    static boolean exists(HandleCategory category, String canonicalName) {
        try {
            return switch (category) {
                case MATERIAL -> Material.getMaterial(canonicalName) != null;
                case ENCHANTMENT -> Enchantment.getByKey(minecraft(key(canonicalName))) != null;
                case POTION_EFFECT -> registryHas("EFFECT", key(canonicalName))
                        || registryHas("MOB_EFFECT", key(canonicalName))
                        || legacyPotionEffect(canonicalName);
                case ENTITY_TYPE -> registryHas("ENTITY_TYPE", key(canonicalName))
                        || enumValueOf("org.bukkit.entity.EntityType", canonicalName);
                case ATTRIBUTE -> registryHas("ATTRIBUTE", key(canonicalName))
                        || registryHas("ATTRIBUTE", "generic." + key(canonicalName))
                        || enumValueOf("org.bukkit.attribute.Attribute", canonicalName);
                case PARTICLE -> registryHas("PARTICLE_TYPE", key(canonicalName))
                        || enumValueOf("org.bukkit.Particle", canonicalName);
                case SOUND -> enumValueOf("org.bukkit.Sound", canonicalName)
                        || registryHas("SOUNDS", key(canonicalName).replace('_', '.'));
            };
        } catch (Throwable failedProbe) {
            // Any reflective/linkage failure means "not resolvable here" — the compiler warn-skips.
            return false;
        }
    }

    /** The minecraft-namespace registry key for a canonical name (lower-case). */
    private static String key(String canonicalName) {
        return canonicalName.toLowerCase(Locale.ROOT);
    }

    private static NamespacedKey minecraft(String key) {
        return NamespacedKey.minecraft(key);
    }

    /**
     * Reflectively read {@code Registry.<field>} and look the key up. The field is read by
     * reflection because which {@code Registry} constants exist varies by version (e.g. no
     * potion-effect registry on the floor); a {@link Registry}/{@link NamespacedKey} cast and
     * {@code get(NamespacedKey)} call are safe because those are stable across the range.
     */
    private static boolean registryHas(String field, String key) {
        try {
            Field f = Registry.class.getField(field);
            Object value = f.get(null);
            if (!(value instanceof Registry<?> registry)) {
                return false;
            }
            return registry.get(minecraft(key)) != null;
        } catch (Throwable absent) {
            return false;
        }
    }

    /** Reflective {@code EnumType.valueOf(name)} so a now-interface type is never hard-linked. */
    private static boolean enumValueOf(String className, String name) {
        try {
            Class<?> type = Class.forName(className);
            if (!type.isEnum()) {
                return false;
            }
            Method valueOf = type.getMethod("valueOf", String.class);
            valueOf.invoke(null, name);
            return true;
        } catch (Throwable notFound) {
            return false; // unknown name (InvocationTargetException) or not an enum here
        }
    }

    /** The floor path for potion effects, which have no Registry constant before ~1.20.2. */
    private static boolean legacyPotionEffect(String name) {
        try {
            return PotionEffectType.getByName(name) != null;
        } catch (Throwable removed) {
            return false;
        }
    }
}
