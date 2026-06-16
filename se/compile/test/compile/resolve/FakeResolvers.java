package compile.resolve;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A pure in-memory {@link PlatformResolvers} for unit tests — the proof that the
 * compiler's cross-version seam needs no Bukkit (docs/architecture.md §2.1).
 * Tokens are matched case-insensitively; unmapped tokens resolve to empty,
 * exercising the compiler's warn-and-skip path.
 */
public final class FakeResolvers implements PlatformResolvers {

    private final Map<String, Integer> materials;
    private final Map<String, Integer> sounds;
    private final Map<String, Integer> potions;
    private final Map<String, Integer> enchants;
    private final Map<String, Integer> entities;
    private final Map<String, Integer> attributes;

    private FakeResolvers(Builder b) {
        this.materials = b.materials;
        this.sounds = b.sounds;
        this.potions = b.potions;
        this.enchants = b.enchants;
        this.entities = b.entities;
        this.attributes = b.attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public OptionalInt material(String token) {
        return get(materials, token);
    }

    @Override
    public OptionalInt sound(String token) {
        return get(sounds, token);
    }

    @Override
    public OptionalInt potionEffect(String token) {
        return get(potions, token);
    }

    @Override
    public OptionalInt enchantment(String token) {
        return get(enchants, token);
    }

    @Override
    public OptionalInt entityType(String token) {
        return get(entities, token);
    }

    @Override
    public OptionalInt attribute(String token) {
        return get(attributes, token);
    }

    private static OptionalInt get(Map<String, Integer> map, String token) {
        Integer v = map.get(token.trim().toUpperCase(Locale.ROOT));
        return v == null ? OptionalInt.empty() : OptionalInt.of(v);
    }

    /** Fluent builder; each entry maps a (case-insensitive) token to an interned id. */
    public static final class Builder {
        private final Map<String, Integer> materials = new HashMap<>();
        private final Map<String, Integer> sounds = new HashMap<>();
        private final Map<String, Integer> potions = new HashMap<>();
        private final Map<String, Integer> enchants = new HashMap<>();
        private final Map<String, Integer> entities = new HashMap<>();
        private final Map<String, Integer> attributes = new HashMap<>();

        public Builder material(String token, int id) {
            materials.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public Builder sound(String token, int id) {
            sounds.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public Builder potionEffect(String token, int id) {
            potions.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public Builder enchantment(String token, int id) {
            enchants.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public Builder entityType(String token, int id) {
            entities.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public Builder attribute(String token, int id) {
            attributes.put(token.toUpperCase(Locale.ROOT), id);
            return this;
        }

        public FakeResolvers build() {
            return new FakeResolvers(this);
        }
    }
}
