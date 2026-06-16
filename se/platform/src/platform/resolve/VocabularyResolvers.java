package platform.resolve;

import compile.model.Interner;
import compile.resolve.PlatformResolvers;
import schema.spec.HandleCategory;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link PlatformResolvers} backed by an explicit per-category <em>vocabulary</em> of
 * canonical names that exist on the target version (docs/architecture.md §9). It is the
 * pure core of cross-version resolution: a token is resolved via {@link HandleResolver}
 * (modern &rarr; legacy alias &rarr; reverse alias) against the vocabulary, then interned
 * to a dense id the compiled effect carries. The Bukkit-backed resolver mirrors this
 * exactly, substituting a live {@code Registry}/{@code valueOf} existence check for the
 * vocabulary set and adding the id&rarr;handle lookup the runtime needs.
 *
 * <p>Being pure, this resolver lets the compiler resolve handles in unit tests with
 * realistic, alias-aware behaviour (rather than a hand-stubbed fake), and it is the
 * single, server-free home for the §9 resolution logic.
 */
public final class VocabularyResolvers implements PlatformResolvers {

    private final Map<HandleCategory, Set<String>> available;
    private final Map<HandleCategory, Interner> interners = new EnumMap<>(HandleCategory.class);

    /**
     * @param available per-category set of canonical upper-case names that exist on the
     *                  target version (the vocabulary the resolver matches against)
     */
    public VocabularyResolvers(Map<HandleCategory, Set<String>> available) {
        Objects.requireNonNull(available, "available");
        this.available = new EnumMap<>(HandleCategory.class);
        for (HandleCategory category : HandleCategory.values()) {
            this.available.put(category, Set.copyOf(available.getOrDefault(category, Set.of())));
            this.interners.put(category, new Interner());
        }
    }

    @Override
    public java.util.OptionalInt material(String token) {
        return resolve(HandleCategory.MATERIAL, token);
    }

    @Override
    public java.util.OptionalInt sound(String token) {
        return resolve(HandleCategory.SOUND, token);
    }

    @Override
    public java.util.OptionalInt potionEffect(String token) {
        return resolve(HandleCategory.POTION_EFFECT, token);
    }

    @Override
    public java.util.OptionalInt particle(String token) {
        return resolve(HandleCategory.PARTICLE, token);
    }

    @Override
    public java.util.OptionalInt enchantment(String token) {
        return resolve(HandleCategory.ENCHANTMENT, token);
    }

    @Override
    public java.util.OptionalInt entityType(String token) {
        return resolve(HandleCategory.ENTITY_TYPE, token);
    }

    @Override
    public java.util.OptionalInt attribute(String token) {
        return resolve(HandleCategory.ATTRIBUTE, token);
    }

    /** The canonical name a resolved id maps to (for the runtime's id&rarr;handle lookup), or {@code null}. */
    public String nameOf(HandleCategory category, int id) {
        return interners.get(category).nameOf(id);
    }

    private java.util.OptionalInt resolve(HandleCategory category, String token) {
        Set<String> vocabulary = available.get(category);
        Optional<String> resolved =
                HandleResolver.resolve(token, Aliases.forCategory(category), vocabulary::contains);
        if (resolved.isEmpty()) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(interners.get(category).intern(resolved.get()));
    }
}
