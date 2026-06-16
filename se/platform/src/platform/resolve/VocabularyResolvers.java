package platform.resolve;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import schema.spec.HandleCategory;

/**
 * A {@link RenameResolvers} whose notion of "exists" is an explicit per-category <em>vocabulary</em>
 * of canonical names (docs/architecture.md §9). It is the pure core of cross-version resolution:
 * server-free, so the compiler can resolve handles in unit tests with realistic, alias-aware
 * behaviour rather than a hand-stubbed fake. The Bukkit-backed {@link RegistryResolvers} mirrors it
 * exactly, substituting a live registry/{@code valueOf} existence check for the vocabulary set.
 */
public final class VocabularyResolvers extends RenameResolvers {

    private final Map<HandleCategory, Set<String>> available;

    /**
     * @param available per-category set of canonical upper-case names that exist on the target
     *                  version (the vocabulary the resolver matches against)
     */
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
