package platform.resolve;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import schema.spec.HandleCategory;

/**
 * A {@link RenameResolvers} whose "exists" is an explicit per-category vocabulary (docs/architecture.md §9):
 * the pure, server-free core, so the compiler resolves handles in unit tests with realistic alias-aware
 * behaviour. {@link RegistryResolvers} mirrors it exactly, substituting a live existence check.
 */
public final class VocabularyResolvers extends RenameResolvers {

    private final Map<HandleCategory, Set<String>> available;

    public VocabularyResolvers(Map<HandleCategory, Set<String>> available) {
        Objects.requireNonNull(available, "available");
        this.available = new EnumMap<>(HandleCategory.class);
        for (HandleCategory category : HandleCategory.values()) {
            this.available.put(category, Set.copyOf(available.getOrDefault(category, Set.of())));
        }
    }

    @Override
    protected boolean exists(HandleCategory category, String canonicalName) {
        return available.get(category).contains(canonicalName);
    }
}
