package platform.resolve;

import compile.model.Interner;
import compile.resolve.PlatformResolvers;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import schema.spec.HandleCategory;

/**
 * The shared machinery behind every {@link PlatformResolvers} that resolves version-volatile
 * tokens by cross-version rename (docs/architecture.md §9): it runs each token through
 * {@link HandleResolver} (token &rarr; forward alias &rarr; reverse alias) against a per-category
 * {@code exists} check, then interns the resolved canonical name to a dense id and remembers the
 * id&rarr;name mapping the runtime needs. The only thing a concrete resolver supplies is
 * <em>what "exists" means</em>: a fixed vocabulary set ({@link VocabularyResolvers}, for the pure
 * compiler/tests) or a live server lookup ({@link RegistryResolvers}, in production).
 */
public abstract class RenameResolvers implements PlatformResolvers {

    private final Map<HandleCategory, Interner> interners = new EnumMap<>(HandleCategory.class);

    protected RenameResolvers() {
        for (HandleCategory category : HandleCategory.values()) {
            interners.put(category, new Interner());
        }
    }

    /**
     * Whether {@code canonicalName} (an upper-case Bukkit-style name) actually resolves on the
     * target platform for {@code category}. Called by {@link HandleResolver} for the token and its
     * alias forms; must never throw (a concrete resolver swallows lookup failures as {@code false}).
     */
    protected abstract boolean exists(HandleCategory category, String canonicalName);

    /** The canonical name a resolved id maps to (the runtime's id&rarr;handle lookup), or {@code null}. */
    public final String nameOf(HandleCategory category, int id) {
        return interners.get(category).nameOf(id);
    }

    private OptionalInt resolve(HandleCategory category, String token) {
        Optional<String> resolved = HandleResolver.resolve(
                token, Aliases.forCategory(category), name -> exists(category, name));
        return resolved.map(name -> OptionalInt.of(interners.get(category).intern(name)))
                .orElseGet(OptionalInt::empty);
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
