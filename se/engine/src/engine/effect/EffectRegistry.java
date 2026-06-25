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
 * Explicit, greppable registry of effect kinds (docs/architecture.md §7, §13.2) — deliberately not
 * annotation-processor codegen, so a contributor can see the wiring. Heads match case-insensitively;
 * a duplicate fails fast at build time.
 *
 * <p>Also the seam that keeps the compiler pure yet affinity-aware: {@link #specRegistry()} and
 * {@link #affinityOf()} are built at boot and injected into the compiler, so {@code se-compile} never
 * depends on {@code se-engine} (§2.1).
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

    /** A {@link SpecRegistry} view over each kind's {@link ParamSpec}, for the compiler's validation stage. */
    public SpecRegistry specRegistry() {
        ParamSpec[] specs = byHead.values().stream()
                .map(k -> k.spec().paramSpec())
                .toArray(ParamSpec[]::new);
        return MapSpecRegistry.of(specs);
    }

    /** Head &rarr; declared {@link Affinity} for the compiler's fold; {@code null} for an unknown head (compiler then defaults to {@code CONTEXT_LOCAL}). */
    public Function<String, Affinity> affinityOf() {
        return head -> {
            EffectKind k = byHead.get(head.toUpperCase(Locale.ROOT));
            return k == null ? null : k.spec().affinity();
        };
    }

    /**
     * Head &rarr; default-selector head for the compiler's selector lowering: the effect's first target
     * slot's selector type, or {@code null} when it declares none (compiler then defaults to {@code SELF}).
     * Inline selectors on the authored line override this (§3.5, §7).
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
