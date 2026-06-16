package schema.spec;

import java.util.Locale;

/**
 * The kind of version-volatile referent a {@code HANDLE}-typed argument names — a
 * material, sound, potion effect, etc. (docs/architecture.md §9). The author writes
 * a token (e.g. {@code STRENGTH}); the compiler's resolve stage turns it into an
 * interned int handle via the matching {@code PlatformResolvers} method, so the
 * runtime never touches a renamed constant. Each category maps one-to-one to a
 * resolver method.
 */
public enum HandleCategory {

    MATERIAL,
    SOUND,
    POTION_EFFECT,
    PARTICLE,
    ENTITY_TYPE,
    ATTRIBUTE,
    ENCHANTMENT;

    /** A short lower-case label for usage/doc strings, e.g. {@code potion_effect}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
