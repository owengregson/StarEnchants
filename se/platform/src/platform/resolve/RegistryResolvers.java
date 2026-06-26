package platform.resolve;

import java.util.Map;
import schema.spec.HandleCategory;

/**
 * The production {@link RenameResolvers}: resolves version-volatile tokens against the LIVE server, so
 * the compiler interns handles guaranteed to exist on this exact Paper/Folia version (docs/architecture.md
 * §9). Bukkit-backed mirror of {@link VocabularyResolvers}; "exists" is a real {@link RegistrySupport}
 * lookup. One instance built at boot and injected into the compiler.
 */
public final class RegistryResolvers extends RenameResolvers {

    @Override
    protected boolean exists(HandleCategory category, String canonicalName) {
        return RegistrySupport.exists(category, canonicalName);
    }

    @Override
    protected Map<String, String> fallbackAliases(HandleCategory category) {
        return RegistrySupport.fallbackAliases(category); // empty on the floor build; legacy degradations on 1.8
    }
}
