package feature.menu;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** The context handed to a {@link ClickAction}: who clicked, the open {@link MenuHolder}, and the {@link ClickType}. */
public record MenuClick(Player player, MenuHolder holder, ClickType type) {

    public MenuClick {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(type, "type");
    }

    public boolean right() {
        return type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
    }

    public boolean shift() {
        return type.isShiftClick();
    }
}
