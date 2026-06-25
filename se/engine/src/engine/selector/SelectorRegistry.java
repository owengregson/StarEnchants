package engine.selector;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import schema.spec.ParamSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of selector kinds (docs/architecture.md §7). Heads match case-insensitively; a
 * duplicate head fails fast at build time.
 *
 * <p>{@link #specRegistry()} exposes each selector's {@link ParamSpec} so the pure compiler can
 * validate inline selector arguments without depending on {@code se-engine} (§2.1).
 */
public final class SelectorRegistry {

    private final Map<String, SelectorKind> byHead;

    private SelectorRegistry(Map<String, SelectorKind> byHead) {
        this.byHead = Map.copyOf(byHead);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Case-insensitive head lookup. */
    public Optional<SelectorKind> lookup(String head) {
        return Optional.ofNullable(byHead.get(head.toUpperCase(Locale.ROOT)));
    }

    /** Every registered head, in canonical upper-case form. */
    public Set<String> heads() {
        return byHead.keySet();
    }

    public Collection<SelectorKind> kinds() {
        return byHead.values();
    }

    /** {@link SpecRegistry} view over each kind's {@link ParamSpec}, for the compiler's selector lowering. */
    public SpecRegistry specRegistry() {
        ParamSpec[] specs = byHead.values().stream()
                .map(k -> k.spec().paramSpec())
                .toArray(ParamSpec[]::new);
        return MapSpecRegistry.of(specs);
    }

    /** Builder enforcing unique, case-insensitive heads. */
    public static final class Builder {

        private final Map<String, SelectorKind> byHead = new LinkedHashMap<>();

        public Builder register(SelectorKind kind) {
            Objects.requireNonNull(kind, "kind");
            String key = kind.spec().head().toUpperCase(Locale.ROOT);
            if (byHead.putIfAbsent(key, kind) != null) {
                throw new IllegalArgumentException("duplicate selector head: " + key);
            }
            return this;
        }

        public SelectorRegistry build() {
            return new SelectorRegistry(byHead);
        }
    }
}
