package feature.guard;

import com.destroystokyo.paper.event.inventory.PrepareGrindstoneEvent;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;

/**
 * Modern vanilla-station guard (G04/G05/G06): nulls the prepared result of a smithing / grindstone / anvil
 * operation that {@link StationGuardRules} rejects, so the station reads as "no valid recipe" (its own empty-
 * result UX) and there is nothing to take. HIGHEST is the final legitimate say over other prepare listeners.
 *
 * <p>The grindstone handler binds {@code com.destroystokyo.paper.event.inventory.PrepareGrindstoneEvent}, not the
 * {@code org.bukkit} twin: that twin only exists from 1.19.3 and is NOT on the 1.17.1 compile floor. The floor's
 * server fires the destroystokyo class directly, and every later version's {@code org.bukkit} PrepareGrindstone
 * event extends it (shared handler list; the executor's instanceof filter keeps it grindstone-only), so this one
 * handler compiles on the floor and receives the event on the whole 1.17.1&rarr;26.1.2 range. If the compile
 * floor is ever raised past 1.19.3, swap this to the {@code org.bukkit} twin.
 *
 * <p>Anvil interplay: the guard's empty-right-slot exemption structurally spares the nametag virtual anvil
 * ({@code ModernAnvilRename}) — {@code NametagListener} cancels every click in that dialog, so its slot 1 can
 * never be populated, and a bare rename is never rejected here regardless of listener order.
 */
public final class ModernStationGuard implements Listener {

    private final StationGuardRules rules;

    public ModernStationGuard(StationGuardRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (rules.blockSmithing(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (rules.blockGrindstone(event.getInventory().getItem(0), event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (rules.blockAnvil(event.getInventory().getItem(0), event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }
}
