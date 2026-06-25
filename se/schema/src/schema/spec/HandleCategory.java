package schema.spec;

import java.util.Locale;

/**
 * The kind of version-volatile referent a {@code HANDLE} argument names (docs/architecture.md §9).
 * Resolve interns the authored token to an int handle so the runtime never touches a renamed
 * constant; each category maps one-to-one to a {@code PlatformResolvers} method.
 */
public enum HandleCategory {

    MATERIAL,
    SOUND,
    POTION_EFFECT,
    PARTICLE,
    ENTITY_TYPE,
    ATTRIBUTE,
    ENCHANTMENT;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
