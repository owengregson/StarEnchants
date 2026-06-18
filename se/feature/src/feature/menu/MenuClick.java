package feature.menu;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * The context handed to a {@link ClickAction}: who clicked, the open {@link MenuHolder} (carrying the
 * per-open navigation state — page / sub-view / selection), and the {@link ClickType} (left/right/shift so
 * a button can branch on the mouse button). Immutable; constructed by {@link MenuListener} for each click.
 */
public record MenuClick(Player player, MenuHolder holder, ClickType type) {

    public MenuClick {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(type, "type");
    }

    /** Whether this was a right-click (the common "secondary action" branch). */
    public boolean right() {
        return type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
    }

    /** Whether shift was held (some menus use shift-click for a bulk/alternate action). */
    public boolean shift() {
        return type.isShiftClick();
    }
}
