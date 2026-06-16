package schema.spec;

import java.util.Objects;

/**
 * One named, positional argument of a {@link ParamSpec}: a {@code name}, its
 * {@link ParamType}, and a doc string.
 *
 * <p>The name is how the value is read back from {@link Args} (e.g.
 * {@code args.dbl("damage")}) and how the migrator maps legacy positional args by
 * meaning rather than index (docs/architecture.md §7, §10).
 */
public record Param(String name, ParamType type, String doc) {

    public Param {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("param name must not be blank");
        }
    }

    public static Param of(String name, ParamType type) {
        return new Param(name, type, "");
    }

    public static Param of(String name, ParamType type, String doc) {
        return new Param(name, type, doc == null ? "" : doc);
    }

    public boolean required() {
        return type.isRequired();
    }
}
