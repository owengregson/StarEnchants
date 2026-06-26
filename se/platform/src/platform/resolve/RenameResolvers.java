package platform.resolve;

import compile.model.Interner;
import compile.resolve.PlatformResolvers;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import schema.spec.HandleCategory;

/**
 * Shared cross-version resolve-and-intern machinery behind every {@link PlatformResolvers}
 * (docs/architecture.md §9): runs each token through {@link HandleResolver}, interns the resolved name to
 * a dense id, and keeps the id&rarr;name mapping. A concrete resolver supplies only what "exists" means —
 * a fixed vocabulary ({@link VocabularyResolvers}) or a live lookup ({@link RegistryResolvers}).
 */
public abstract class RenameResolvers implements PlatformResolvers {

    private final Map<HandleCategory, Interner> interners = new EnumMap<>(HandleCategory.class);

    protected RenameResolvers() {
        for (HandleCategory category : HandleCategory.values()) {
            interners.put(category, new Interner());
        }
    }

    /**
     * Whether {@code canonicalName} resolves on the target platform. Called by {@link HandleResolver} for
     * the token and its alias forms; must never throw (swallow lookup failures as {@code false}).
     */
    protected abstract boolean exists(HandleCategory category, String canonicalName);

    /**
     * Platform-specific, lossy fallbacks merged ON TOP of the shared {@link Aliases} renames for resolution
     * only — e.g. on the optional 1.8 lane a {@code SOUL} particle (added 1.16) degrades to a 1.8 particle.
     * These are NOT renames (the migrator must not see them, so they never enter {@link Aliases}); empty by
     * default, supplied by the live resolver per target.
     */
    protected Map<String, String> fallbackAliases(HandleCategory category) {
        return Map.of();
    }

    /** The canonical name a resolved id maps to, or {@code null}. */
    public final String nameOf(HandleCategory category, int id) {
        return interners.get(category).nameOf(id);
    }

    private OptionalInt resolve(HandleCategory category, String token) {
        Optional<String> resolved = HandleResolver.resolve(
                token, aliasesFor(category), name -> exists(category, name));
        return resolved.map(name -> OptionalInt.of(interners.get(category).intern(name)))
                .orElseGet(OptionalInt::empty);
    }

    private Map<String, String> aliasesFor(HandleCategory category) {
        Map<String, String> renames = Aliases.forCategory(category);
        Map<String, String> fallbacks = fallbackAliases(category);
        if (fallbacks.isEmpty()) {
            return renames;
        }
        Map<String, String> merged = new java.util.HashMap<>(renames);
        merged.putAll(fallbacks); // platform fallback wins on the rare key clash
        return merged;
    }

    @Override
    public final OptionalInt material(String token) {
        return resolve(HandleCategory.MATERIAL, token);
    }

    @Override
    public final OptionalInt sound(String token) {
        return resolve(HandleCategory.SOUND, token);
    }

    @Override
    public final OptionalInt potionEffect(String token) {
        return resolve(HandleCategory.POTION_EFFECT, token);
    }

    @Override
    public final OptionalInt particle(String token) {
        return resolve(HandleCategory.PARTICLE, token);
    }

    @Override
    public final OptionalInt enchantment(String token) {
        return resolve(HandleCategory.ENCHANTMENT, token);
    }

    @Override
    public final OptionalInt entityType(String token) {
        return resolve(HandleCategory.ENTITY_TYPE, token);
    }

    @Override
    public final OptionalInt attribute(String token) {
        return resolve(HandleCategory.ATTRIBUTE, token);
    }
}
