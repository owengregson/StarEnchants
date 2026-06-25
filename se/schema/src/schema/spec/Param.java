package schema.spec;

import java.util.Objects;

/**
 * One named, positional {@link ParamSpec} argument. The name reads the value back from
 * {@link Args} and lets the migrator map legacy positional args by meaning, not index
 * (docs/architecture.md §10).
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
