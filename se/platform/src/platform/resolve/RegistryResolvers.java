package platform.resolve;

import schema.spec.HandleCategory;

/**
 * The production {@link RenameResolvers}: it resolves version-volatile tokens against the LIVE
 * server, so the compiler turns authored names like {@code CONFUSION}, {@code DAMAGE_ALL},
 * {@code SULPHUR}, or {@code GENERIC_MAX_HEALTH} into interned handles that are guaranteed to exist
 * on this exact Paper/Folia version (docs/architecture.md §9). It is the Bukkit-backed mirror of
 * {@link VocabularyResolvers}: same alias machinery, but "exists" is a real registry/{@code valueOf}
 * lookup ({@link RegistrySupport}) instead of a fixed vocabulary. Stateless beyond the base's
 * interners — one instance is built at boot and injected into the compiler.
 */
public final class RegistryResolvers extends RenameResolvers {

    @Override
    protected boolean exists(HandleCategory category, String canonicalName) {
        return RegistrySupport.exists(category, canonicalName);
    }
}
