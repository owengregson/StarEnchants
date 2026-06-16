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
 * The explicit, greppable registry of selector kinds (docs/architecture.md §7) — the
 * selector counterpart of {@link engine.effect.EffectRegistry}. Heads are matched
 * case-insensitively and a duplicate head fails fast at build time.
 *
 * <p>{@link #specRegistry()} exposes each selector's {@link ParamSpec} so the (pure)
 * compiler can validate inline selector arguments without depending on
 * {@code se-engine}; the engine builds the registry at boot and injects this view —
 * the same seam the effect registry uses to keep the compiler pure (§2.1).
 */
public final class SelectorRegistry {

    private final Map<String, SelectorKind> byHead;

    private SelectorRegistry(Map<String, SelectorKind> byHead) {
        this.byHead = Map.copyOf(byHead);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The kind registered under {@code head} (case-insensitive), if any. */
    public Optional<SelectorKind> lookup(String head) {
        return Optional.ofNullable(byHead.get(head.toUpperCase(Locale.ROOT)));
    }

    /** Every registered head, in canonical upper-case form. */
    public Set<String> heads() {
        return byHead.keySet();
    }

    /** Every registered kind. */
    public Collection<SelectorKind> kinds() {
        return byHead.values();
    }

    /**
     * A {@link SpecRegistry} view backed by each kind's {@link ParamSpec}, for
     * injection into the compiler's selector lowering.
     */
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
