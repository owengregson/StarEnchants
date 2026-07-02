package feature.slot;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Slot-expander gesture glue (§H): slot item on the cursor + click on gear raises its enchant-slot count.
 * Logic lives in {@link SlotService}. A thin leaf of the shared {@link ApplyGestureListener} (ADR-0041).
 */
public final class SlotListener extends ApplyGestureListener {

    private final SlotService service;

    public SlotListener(SlotService service, Messages messages, Sounds sounds) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isSlotItem(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
    }
}
