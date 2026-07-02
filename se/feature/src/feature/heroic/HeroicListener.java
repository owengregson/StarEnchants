package feature.heroic;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Heroic upgrade gesture glue (docs/v3-directives.md §F); logic lives in {@link HeroicService}. A thin leaf of
 * the shared {@link ApplyGestureListener} (ADR-0041).
 */
public final class HeroicListener extends ApplyGestureListener {

    private final HeroicService service;

    public HeroicListener(HeroicService service, Messages messages, Sounds sounds) {
        super(messages, sounds);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isUpgrade(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
    }
}
