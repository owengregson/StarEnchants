package engine.selector;

import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/**
 * One target-selector kind — a stateless, self-describing way to turn an activation
 * into the set of entities an effect acts on (docs/architecture.md §3.5, §7,
 * se-engine/selector "{@code Self/Victim/Attacker/Aoe/Nearest/...}"). An effect
 * declares which selector fills each of its target slots; at activation the engine
 * runs the bound selector once and hands the result to the effect via
 * {@code EffectCtx.targets(name)} — the hot path never parses a selector.
 *
 * <p>Implementations MUST be stateless (a single shared instance is reused across all
 * activations and threads). Adding a selector is implementing this interface and
 * registering it in one place ({@link engine.selector.kind.BuiltinSelectors}), with
 * no other code to edit — the same extensibility rule as effects.
 */
public interface SelectorKind {

    /** This kind's self-describing signature: its named arguments (§7). */
    SelectorSpec spec();

    /**
     * Resolve the entities this selector targets for one activation. Reads the actors
     * and arguments from {@code ctx}; returns an empty list (never {@code null}) when
     * nothing matches. A LOCATION selector (block/coordinate targeting) overrides
     * {@link #resolveLocations} instead and leaves this empty.
     */
    default List<LivingEntity> resolve(SelectorCtx ctx) {
        return List.of();
    }

    /**
     * Resolve the LOCATIONS this selector targets for one activation (block/coordinate selectors —
     * {@code @Block}/{@code @Trench}/{@code @Vein}/…, docs/v3-directives.md §A). Entity selectors leave
     * this empty (the default); the engine resolves BOTH channels and a location-consuming effect
     * (e.g. {@code SET_BLOCK}/{@code BREAK_BLOCK}) reads {@code EffectCtx.targetLocations}. Never null.
     */
    default List<Location> resolveLocations(SelectorCtx ctx) {
        return List.of();
    }

    /** The canonical head this kind registers under, e.g. {@code AOE}. */
    default String head() {
        return spec().head();
    }
}
