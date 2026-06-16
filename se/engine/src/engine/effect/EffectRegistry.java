package engine.effect;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import compile.model.Affinity;
import engine.spec.TargetSpec;
import schema.spec.ParamSpec;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The explicit, greppable registry of effect kinds (docs/architecture.md §7,
 * §13.2) — the primary registration mechanism, deliberately not annotation-processor
 * codegen, so a contributor can <em>see</em> the wiring. Heads are matched
 * case-insensitively and a duplicate head fails fast at build time.
 *
 * <p>It is also the seam that keeps the compiler pure while making it affinity-aware:
 * {@link #specRegistry()} exposes each kind's {@link ParamSpec} for validation and
 * {@link #affinityOf()} exposes each kind's declared {@link Affinity} for the
 * compile-time fold. The engine builds these at boot and injects them into the
 * compiler, so {@code se-compile} never depends on {@code se-engine} (§2.1).
 */
public final class EffectRegistry {

    private final Map<String, EffectKind> byHead;

    private EffectRegistry(Map<String, EffectKind> byHead) {
        this.byHead = Map.copyOf(byHead);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The kind registered under {@code head} (case-insensitive), if any. */
    public Optional<EffectKind> lookup(String head) {
        return Optional.ofNullable(byHead.get(head.toUpperCase(Locale.ROOT)));
    }

    /** Every registered head, in canonical upper-case form. */
    public Set<String> heads() {
        return byHead.keySet();
    }

    /** Every registered kind. */
    public Collection<EffectKind> kinds() {
        return byHead.values();
    }

    /**
     * A {@link SpecRegistry} view backed by each kind's {@link ParamSpec}, for
     * injection into the compiler's validation stage.
     */
    public SpecRegistry specRegistry() {
        ParamSpec[] specs = byHead.values().stream()
                .map(k -> k.spec().paramSpec())
                .toArray(ParamSpec[]::new);
        return MapSpecRegistry.of(specs);
    }

    /**
     * A head &rarr; declared {@link Affinity} lookup for the compiler's affinity
     * fold; returns {@code null} for an unknown head (the compiler then defaults it
     * to {@code CONTEXT_LOCAL}).
     */
    public Function<String, Affinity> affinityOf() {
        return head -> {
            EffectKind k = byHead.get(head.toUpperCase(Locale.ROOT));
            return k == null ? null : k.spec().affinity();
        };
    }

    /**
     * A head &rarr; declared default-selector head lookup for the compiler's selector
     * lowering: an effect's first declared target slot's selector type (e.g.
     * {@code AOE}), or {@code null} when the effect declares no target — in which case
     * the compiler defaults the effect to {@code SELF}. Inline selectors on the
     * authored line override this (§3.5, §7).
     */
    public Function<String, String> defaultSelectorOf() {
        return head -> {
            EffectKind k = byHead.get(head.toUpperCase(Locale.ROOT));
            if (k == null) {
                return null;
            }
            List<TargetSpec> targets = k.spec().targets();
            return targets.isEmpty() ? null : targets.get(0).selectorType();
        };
    }

    /** Builder enforcing unique, case-insensitive heads. */
    public static final class Builder {

        private final Map<String, EffectKind> byHead = new LinkedHashMap<>();

        public Builder register(EffectKind kind) {
            Objects.requireNonNull(kind, "kind");
            String key = kind.spec().head().toUpperCase(Locale.ROOT);
            if (byHead.putIfAbsent(key, kind) != null) {
                throw new IllegalArgumentException("duplicate effect head: " + key);
            }
            return this;
        }

        public EffectRegistry build() {
            return new EffectRegistry(byHead);
        }
    }
}
