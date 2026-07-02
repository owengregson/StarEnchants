package feature.scroll;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Scroll gesture glue (§I); logic lives in {@link ScrollService}. A thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041).
 */
public final class ScrollListener extends ApplyGestureListener {

    private final ScrollService service;

    public ScrollListener(ScrollService service, Messages messages) {
        super(messages);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isScroll(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.interact(cursor, target);
    }
}
