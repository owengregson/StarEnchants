package engine.run;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * The injected area-scan an {@link AbilityExecutor} hands to area selectors ({@code AOE},
 * {@code NEAREST}) via {@link engine.selector.SelectorCtx#nearbyLiving} (docs/architecture.md §3.6).
 * It is a dependency rather than baked in because the scan is the one part of selector resolution
 * that touches a {@code World} and must run on the centre's region thread on Folia — so the
 * Folia-correct, region-scoped implementation lives in the server-bound wiring layer, while the
 * executor and the selector kinds stay pure and unit-testable.
 */
@FunctionalInterface
public interface AreaScan {

    /** The living entities within {@code radius} blocks of {@code center}; never {@code null}. */
    Iterable<LivingEntity> nearbyLiving(Location center, double radius);

    /**
     * The default for contexts with no area-scan provider: area selectors resolve to nothing. Used
     * until the region-scoped scan is wired with the trigger listener; direct selectors (Self/Victim
     * /Attacker) never call it, so combat that uses only those is unaffected.
     */
    AreaScan NONE = (center, radius) -> List.of();
}
