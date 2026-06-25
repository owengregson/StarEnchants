package engine.trigger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * The canonical trigger vocabulary (docs/architecture.md §3.7) — single source of truth for each trigger's
 * dense id, assigned in registration order. The compiler interns content trigger names against
 * {@link #names()} so a compiled {@code triggerMask} bit means the same trigger the runtime routes.
 */
public final class TriggerRegistry {

    /** The {@code triggerMask} is an {@code int}, so trigger ids must fit in {@code [0,32)}. */
    public static final int MAX_TRIGGERS = 32;

    private final List<TriggerKind> byId;
    private final Map<String, Integer> idByName;

    private TriggerRegistry(List<TriggerKind> byId, Map<String, Integer> idByName) {
        this.byId = List.copyOf(byId);
        this.idByName = Map.copyOf(idByName);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The canonical id of {@code name} (case-insensitive), or empty if unknown. */
    public Optional<Integer> idOf(String name) {
        return Optional.ofNullable(idByName.get(canonical(name)));
    }

    /** The trigger at a canonical id. */
    public TriggerKind byId(int id) {
        return byId.get(id);
    }

    public int count() {
        return byId.size();
    }

    /** The trigger names in canonical id order — what the compiler seeds its interner with. */
    public List<String> names() {
        List<String> out = new ArrayList<>(byId.size());
        for (TriggerKind t : byId) {
            out.add(t.name());
        }
        return out;
    }

    public boolean isAttack(int id) {
        return id >= 0 && id < byId.size() && byId.get(id).direction() == TriggerKind.Direction.ATTACK;
    }

    public boolean isDefense(int id) {
        return id >= 0 && id < byId.size() && byId.get(id).direction() == TriggerKind.Direction.DEFENSE;
    }

    /** Attacker-side classifier for {@code WornFlattener} (interned id &rarr; is-attack). */
    public IntPredicate attackTriggers() {
        return this::isAttack;
    }

    /** Defender-side classifier for {@code WornFlattener} (interned id &rarr; is-defense). */
    public IntPredicate defenseTriggers() {
        return this::isDefense;
    }

    private static String canonical(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    public static final class Builder {

        private final List<TriggerKind> byId = new ArrayList<>();
        private final Map<String, Integer> idByName = new LinkedHashMap<>();

        public Builder register(TriggerKind trigger) {
            Objects.requireNonNull(trigger, "trigger");
            String key = canonical(trigger.name());
            if (idByName.containsKey(key)) {
                throw new IllegalArgumentException("duplicate trigger: " + key);
            }
            if (byId.size() >= MAX_TRIGGERS) {
                throw new IllegalArgumentException(
                        "too many triggers: at most " + MAX_TRIGGERS + " fit in the trigger mask");
            }
            idByName.put(key, byId.size());
            byId.add(trigger);
            return this;
        }

        public TriggerRegistry build() {
            return new TriggerRegistry(byId, idByName);
        }
    }
}
