package feature.combat;

import engine.stores.FoodWindowStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * The one hunger bridge for {@code MODIFY_FOOD}'s window modes: {@code MODIFY_FOOD} arms a per-player window
 * through the per-event sink, and this reads it back on the SEPARATE {@code FoodLevelChangeEvent} — which is
 * where a meal's nutrition and every exhaustion drain actually land, a tick or more after the activation.
 *
 * <p>Both modes read the same event, so they share one listener rather than each minting its own. The
 * direction of the change decides which window applies: an INCREASE is a gain, a DECREASE is a drain, and
 * neither mode can affect the other's direction.
 */
public final class FoodWindowListener implements Listener {

    private final FoodWindowStore windows;
    private final LongSupplier nowTicks;

    public FoodWindowListener(FoodWindowStore windows, LongSupplier nowTicks) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int before = player.getFoodLevel();
        int after = event.getFoodLevel();
        long now = nowTicks.getAsLong();
        if (after < before) {
            if (windows.cancelsDrain(player.getUniqueId(), now)) {
                event.setCancelled(true);
            }
            return;
        }
        if (after <= before) {
            return; // no change: nothing to scale, and scaling zero would be a no-op anyway
        }
        double factor = windows.gainFactor(player.getUniqueId(), now);
        if (factor == 1.0) {
            return;
        }
        // Scale the DELTA, not the resulting level — the level already includes what the player had, so
        // multiplying it would hand a nearly-full bar a windfall from a single bite.
        int scaled = before + (int) Math.round((after - before) * factor);
        event.setFoodLevel(Math.max(0, Math.min(20, scaled)));
    }
}
