package feature.reforge;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Reforge apply gesture glue (ADR-0070); logic lives in {@link ReforgeService}. A thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041) with the DEFAULT target claim — reforge-onto-reforge is meaningless
 * (no merge, unlike crystals), so the base's {@code !claimsCursor(target)} rejection stands. The
 * {@code features.reforges} toggle is LIVE (the crystal/mask rule): the listener registers regardless; gating
 * lives in the worn resolver + use listener, and an apply onto gear is a harmless content mutation either way.
 * The EXTRACTOR cursor stays claimed by {@code CrystalListener} ONLY — this leaf claims only reforge-item
 * cursors, so the two listeners never double-handle one cursor.
 */
public final class ReforgeListener extends ApplyGestureListener {

    private final ReforgeService service;

    public ReforgeListener(ReforgeService service, Messages messages, Sounds sounds) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isReforge(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.apply(cursor, target);
    }
}
