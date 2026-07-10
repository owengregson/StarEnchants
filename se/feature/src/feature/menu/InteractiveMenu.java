package feature.menu;

import java.util.Set;
import org.bukkit.entity.Player;

/**
 * A menu with item-<strong>input slots</strong> the player may place items into (docs/v3-directives.md §K —
 * the merchant benches). Declares which top-inventory slots {@link MenuListener} leaves free for placement
 * (every other slot stays locked) and gets a close hook to return staged inputs so a closed bench never eats
 * items — the dupe/loss guard a Cosmic Enchants-style framework lacked.
 */
public interface InteractiveMenu {

    /** The top-inventory raw slots that accept item placement; everything else is locked. */
    Set<Integer> inputSlots();

    /** On close, return any items left in the input slots so nothing is lost. */
    default void onClose(Player player, MenuHolder holder) {
    }

    /**
     * After an input slot's content changed (fired one tick after the placing/removing click applies, while
     * the menu is still open) — a bench refreshes its live preview cells here. Previews live in LOCKED slots,
     * so they are display-only and never returned by {@link #onClose}.
     */
    default void onInputChanged(Player player, MenuHolder holder) {
    }
}
