package feature.trak;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import platform.lang.Messages;

/**
 * Trak-gem glue (§I); logic lives in {@link TrakService}. The apply gesture is a thin leaf of the shared
 * {@link ApplyGestureListener} (ADR-0041); block breaks and kills feed the background lifetime counters, each
 * firing on the acting player's own region thread (Folia-correct).
 */
public final class TrakListener extends ApplyGestureListener {

    private final TrakService service;

    public TrakListener(TrakService service, Messages messages) {
        super(messages);
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isTrakGem(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.trackBlockBreak(event.getPlayer());
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // player deaths arrive via PlayerDeathEvent (its own handler list)
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.trackKill(killer, false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.trackKill(killer, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            service.trackFishCatch(event.getPlayer());
        }
    }
}
