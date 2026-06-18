package engine.condition;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.cond.VarResolver;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The condition variable vocabulary: which {@code %scope.name%} facts exist, their
 * types, and the dense {@link FactBuffer} slot each occupies (docs/architecture.md
 * §3.4). One vocabulary is the single source of truth for both lowering and runtime
 * population — the engine builds it at boot, exposes {@link #asResolver()} to the
 * compiler (so {@code se-compile} stays pure), and sizes {@link #newFactBuffer()} from
 * it, so a compiled condition's slot and the populated buffer always agree.
 *
 * <p>Slots are assigned per kind in registration order: the <i>n</i>th numeric
 * variable gets number slot <i>n</i>, the <i>n</i>th flag gets bit <i>n</i>, etc.
 */
public final class VarVocabulary {

    private final Map<String, VarBinding> byKey;
    private final int numberSlots;
    private final int flagSlots;
    private final int stringSlots;

    private VarVocabulary(Map<String, VarBinding> byKey, int numberSlots, int flagSlots, int stringSlots) {
        this.byKey = Map.copyOf(byKey);
        this.numberSlots = numberSlots;
        this.flagSlots = flagSlots;
        this.stringSlots = stringSlots;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Resolve a {@code %scope.name%} reference to its binding, or empty if unknown. */
    public Optional<VarBinding> lookup(String scope, String name) {
        return Optional.ofNullable(byKey.get(key(scope, name)));
    }

    /** The compiler's view of this vocabulary. */
    public VarResolver asResolver() {
        return this::lookup;
    }

    /**
     * Every declared variable, keyed by its canonical {@code "scope.name"} (lower-case), with the binding
     * carrying its {@link VarKind} and dense slot. An immutable view — the enumeration the {@code /se}
     * reference (§J) and the in-game reference browser (§K) need to list the variable vocabulary, which
     * {@link #lookup} alone could not provide.
     */
    public Map<String, VarBinding> bindings() {
        return byKey;
    }

    public int numberSlots() {
        return numberSlots;
    }

    public int flagSlots() {
        return flagSlots;
    }

    public int stringSlots() {
        return stringSlots;
    }

    /** A fresh {@link FactBuffer} sized to hold every variable in this vocabulary. */
    public FactBuffer newFactBuffer() {
        return new FactBuffer(numberSlots, flagSlots, stringSlots);
    }

    private static String key(String scope, String name) {
        String raw = scope == null || scope.isBlank() ? name : scope + "." + name;
        return raw.toLowerCase(Locale.ROOT);
    }

    /** Builder assigning dense per-kind slots in registration order. */
    public static final class Builder {

        private final Map<String, VarBinding> byKey = new LinkedHashMap<>();
        private int numberSlots;
        private int flagSlots;
        private int stringSlots;

        /** Declare a numeric variable (e.g. {@code victim.health}). */
        public Builder number(String key) {
            return add(key, new VarBinding(VarKind.NUM, numberSlots++));
        }

        /** Declare a boolean flag variable (e.g. {@code sneaking}). */
        public Builder flag(String key) {
            return add(key, new VarBinding(VarKind.BOOL, flagSlots++));
        }

        /** Declare a string variable. */
        public Builder string(String key) {
            return add(key, new VarBinding(VarKind.STR, stringSlots++));
        }

        private Builder add(String key, VarBinding binding) {
            Objects.requireNonNull(key, "key");
            String canon = key.toLowerCase(Locale.ROOT);
            if (byKey.putIfAbsent(canon, binding) != null) {
                throw new IllegalArgumentException("duplicate condition variable '" + key + "'");
            }
            return this;
        }

        public VarVocabulary build() {
            if (flagSlots > FactBuffer.MAX_FLAGS) {
                throw new IllegalArgumentException("at most " + FactBuffer.MAX_FLAGS + " flag variables");
            }
            return new VarVocabulary(byKey, numberSlots, flagSlots, stringSlots);
        }
    }
}
