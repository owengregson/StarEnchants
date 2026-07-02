package engine.selector;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import schema.spec.ParamSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of selector kinds. Heads match case-insensitively; a duplicate fails fast at build time.
 * {@link #specRegistry()} exposes each selector's {@link ParamSpec} so the pure compiler can validate
 * inline arguments without depending on {@code se-engine}.
 *
 * <p>Each head gets a dense {@code kindId} in registration order (ADR-0039): {@link #idOf} stamps it onto a
 * {@link compile.model.CompiledSelector} at compile time and {@link #selectorsById()} lets the executor
 * dispatch by array index with no per-execution head lookup.
 */
public final class SelectorRegistry {

    private final Map<String, SelectorKind> byHead;
    private final SelectorKind[] byId;
    private final Map<String, Integer> idByHead;

    // {@code ordered} MUST preserve registration order (a LinkedHashMap) so dense ids match the array positions.
    private SelectorRegistry(Map<String, SelectorKind> ordered) {
        this.byHead = Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
        this.byId = ordered.values().toArray(new SelectorKind[0]);
        Map<String, Integer> ids = new LinkedHashMap<>();
        int i = 0;
        for (String head : ordered.keySet()) {
            ids.put(head, i++);
        }
        this.idByHead = Collections.unmodifiableMap(ids);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Case-insensitive head lookup. */
    public Optional<SelectorKind> lookup(String head) {
        return Optional.ofNullable(byHead.get(head.toUpperCase(Locale.ROOT)));
    }

    /** The dense kind id of {@code head} (case-insensitive), or {@code -1} if unregistered (ADR-0039). */
    public int idOf(String head) {
        Integer id = idByHead.get(head.toUpperCase(Locale.ROOT));
        return id == null ? -1 : id;
    }

    /** Selectors indexed by dense id, so the executor dispatches {@code selectorsById()[target.kindId()]} (ADR-0039). */
    public SelectorKind[] selectorsById() {
        return byId.clone();
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
