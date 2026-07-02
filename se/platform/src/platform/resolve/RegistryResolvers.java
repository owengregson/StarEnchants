package platform.resolve;

import java.util.Map;
import schema.spec.HandleCategory;

/**
 * The production {@link RenameResolvers}: resolves version-volatile tokens against the LIVE server, so
 * the compiler interns handles guaranteed to exist on this exact Paper/Folia version (docs/architecture.md
 * §9). Bukkit-backed mirror of {@link VocabularyResolvers}; "exists" is a real {@link HandleLookup} probe. The
 * era impl is chosen by the {@link HandleLookups} bindings twin, so the preserved no-arg ctor (built at boot,
 * and below the composition root by {@code ContentCompiler.production()} + the tester eras) stays era-correct.
 */
public final class RegistryResolvers extends RenameResolvers {

    private final HandleLookup lookup = HandleLookups.current();

    @Override
    protected boolean exists(HandleCategory category, String canonicalName) {
        return lookup.exists(category, canonicalName);
    }

    @Override
    protected Map<String, String> fallbackAliases(HandleCategory category) {
        return lookup.fallbackAliases(category); // empty on the floor build; legacy degradations on 1.8
    }
}
