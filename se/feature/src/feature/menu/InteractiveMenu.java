package feature.menu;

import java.util.Set;
import org.bukkit.entity.Player;

/**
 * A menu with item-<strong>input slots</strong> the player may place items into (docs/v3-directives.md §K —
 * the merchant benches). Most menus are display-only (every click cancelled); a menu that implements this
 * interface tells the shared {@link MenuListener} which top-inventory slots are free for item placement, and
 * gets a close hook to return any staged inputs so a closed bench never eats items.
 *
 * <p>The listener still locks every NON-input slot (buttons + filler) and cancels the slot-crossing actions
 * (shift-click, number-key, drag, …) so an item can only ever land in a declared input slot or the player's
 * own inventory — the dupe/loss guard a Cosmic Enchants-style framework lacked.
 */
public interface InteractiveMenu {

    /** The top-inventory raw slots that accept item placement (everything else is locked). */
    Set<Integer> inputSlots();

    /** Called when the view closes — return any items left in the input slots so nothing is lost. */
    default void onClose(Player player, MenuHolder holder) {
    }
}
