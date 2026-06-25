package engine.selector;

import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import java.util.List;

/**
 * One target-selector kind: turns an activation into the entities (or locations) an effect acts on
 * (docs/architecture.md §3.5, §7). The engine runs the bound selector once per activation; the hot
 * path never parses a selector.
 *
 * <p>Implementations MUST be stateless — one shared instance is reused across all activations and
 * threads. Adding a selector is implementing this interface plus one line in
 * {@link engine.selector.kind.BuiltinSelectors}.
 */
public interface SelectorKind {

    SelectorSpec spec();

    /** Entities targeted this activation; never null. Location selectors override {@link #resolveLocations} and leave this empty. */
    default List<LivingEntity> resolve(SelectorCtx ctx) {
        return List.of();
    }

    /**
     * Locations targeted this activation (block/coordinate selectors, §A); never null. The engine resolves
     * both channels; a location-consuming effect ({@code SET_BLOCK}/{@code BREAK_BLOCK}) reads them.
     */
    default List<Location> resolveLocations(SelectorCtx ctx) {
        return List.of();
    }

    default String head() {
        return spec().head();
    }
}
