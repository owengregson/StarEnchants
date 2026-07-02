package engine.effect;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import compile.model.Affinity;
import engine.spec.TargetSpec;
import schema.spec.ParamSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Explicit, greppable registry of effect kinds (§7, §13.2); heads match case-insensitively, a duplicate fails
 * fast at build. Also the seam that keeps the compiler pure yet affinity-aware: {@link #specRegistry()} and
 * {@link #affinityOf()} are built at boot and injected into the compiler, so {@code se-compile} never depends
 * on {@code se-engine} (§2.1).
 *
 * <p>Each head gets a dense {@code kindId} in registration order (ADR-0039): {@link #idOf} stamps it onto a
 * {@link compile.model.CompiledEffect} at compile time and {@link #kindsById()} lets the executor dispatch by
 * array index with no per-execution head lookup. Built-ins register in a fixed order and add-ons append
 * (ADR-0038), so a head's id is stable across reloads — a snapshot compiled at generation N only references
 * ids that are a prefix of generation N+1's array.
 */
public final class EffectRegistry {

    private final Map<String, EffectKind> byHead;
    private final EffectKind[] byId;
    private final Map<String, Integer> idByHead;

    // {@code ordered} MUST preserve registration order (a LinkedHashMap) so dense ids are assigned in that
    // order; Map.copyOf would drop the order, aliasing an id to the wrong head across reloads.
    private EffectRegistry(Map<String, EffectKind> ordered) {
        this.byHead = Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
        this.byId = ordered.values().toArray(new EffectKind[0]);
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

    /** The kind registered under {@code head} (case-insensitive), if any. */
    public Optional<EffectKind> lookup(String head) {
        return Optional.ofNullable(byHead.get(head.toUpperCase(Locale.ROOT)));
    }

    /** The dense kind id of {@code head} (case-insensitive), or {@code -1} if unregistered (ADR-0039). */
    public int idOf(String head) {
        Integer id = idByHead.get(head.toUpperCase(Locale.ROOT));
        return id == null ? -1 : id;
    }

    /** Kinds indexed by dense id, so the executor dispatches {@code kindsById()[effect.kindId()]} (ADR-0039). */
    public EffectKind[] kindsById() {
        return byId.clone();
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

        // LinkedHashMap: registration order fixes the dense kind ids (ADR-0039), so it must be preserved to build().
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
