package feature.mask;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Mask apply gesture glue (ADR-0053 §3, ADR-0074); logic lives in {@link MaskService}. A thin leaf of the
 * shared {@link ApplyGestureListener} (ADR-0041) which, like {@code CrystalListener}, additionally claims the
 * mask-onto-mask FOLD — so it widens the accepted click shapes and lets a mask target another mask. The
 * {@code features.masks} toggle is LIVE (the crystal rule): the listener registers regardless; gating
 * lives in the worn resolver, and an apply onto gear is a harmless content mutation either way.
 */
public final class MaskListener extends ApplyGestureListener {

    private final MaskService service;

    public MaskListener(MaskService service, Messages messages, Sounds sounds) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsClick(InventoryClickEvent event) {
        // SWAP_WITH_CURSOR is the mask-onto-mask fold gesture; shift/number/double clicks stay excluded.
        return super.claimsClick(event) || event.getAction() == InventoryAction.SWAP_WITH_CURSOR;
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isMask(cursor);
    }

    @Override
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        return true; // mask-onto-mask folds into a composite, so a mask target is claimed too
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.interact(cursor, target);
    }
}
