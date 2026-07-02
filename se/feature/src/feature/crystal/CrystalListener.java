package feature.crystal;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Crystal gesture glue (docs/v3-directives.md §E); logic lives in {@link CrystalService}. A thin leaf of the
 * shared {@link ApplyGestureListener} (ADR-0041); it additionally claims the crystal-onto-crystal merge, so it
 * widens the accepted click shapes and lets a crystal target another crystal.
 */
public final class CrystalListener extends ApplyGestureListener {

    private final CrystalService service;

    public CrystalListener(CrystalService service, Messages messages) {
        super(messages);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsClick(InventoryClickEvent event) {
        // SWAP_WITH_CURSOR is the crystal-onto-crystal merge gesture; shift/number/double clicks stay excluded.
        return super.claimsClick(event) || event.getAction() == InventoryAction.SWAP_WITH_CURSOR;
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isExtractor(cursor) || service.isCrystal(cursor);
    }

    @Override
    protected boolean claimsTarget(ItemStack cursor, ItemStack target) {
        return true; // crystal-onto-crystal merges, so a crystal target is claimed too
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.isExtractor(cursor) ? service.extract(cursor, target) : service.interact(cursor, target);
    }
}
