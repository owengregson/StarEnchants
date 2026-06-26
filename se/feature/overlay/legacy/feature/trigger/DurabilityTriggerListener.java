package feature.trigger;

import java.util.Objects;
import org.bukkit.event.Listener;

/**
 * Legacy (1.8.9) ITEM_DAMAGE trigger — a no-op. 1.8 has no {@code PlayerItemDamageEvent}, so item-damage
 * cannot be observed without an NMS hook; the ITEM_DAMAGE trigger therefore does not fire on 1.8 (a §6
 * "degrades" feature; a Phase-2/Gate-4 NMS durability hook could restore it). Same-FQN counterpart to the
 * {@code overlay/modern} listener; registered but inert.
 */
public final class DurabilityTriggerListener implements Listener {

    public DurabilityTriggerListener(TriggerDispatch dispatch) {
        Objects.requireNonNull(dispatch, "dispatch"); // same ctor shape as modern; no event to subscribe on 1.8
    }
}
