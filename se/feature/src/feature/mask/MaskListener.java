package feature.mask;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Mask apply gesture glue (ADR-0053 §3); logic lives in {@link MaskService}. A thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041) with the DEFAULT target claim — mask-onto-mask is meaningless
 * (no merge, unlike crystals), so the base's {@code !claimsCursor(target)} rejection stands. The
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
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isMask(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.apply(cursor, target);
    }
}
