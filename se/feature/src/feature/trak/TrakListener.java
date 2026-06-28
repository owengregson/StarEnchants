package feature.trak;

import feature.compat.MenuClicks;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Trak-gem glue (§I); logic lives in {@link TrakService}. Dragging a gem onto eligible gear applies it; block
 * breaks and kills feed the background lifetime counters. Folia-correct: each event fires on the acting
 * player's own region thread, where reading/writing their held item is region-safe.
 */
public final class TrakListener implements Listener {

    private final TrakService service;

    public TrakListener(TrakService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (MenuClicks.clickedInventory(event) == null
                || MenuClicks.clickedInventory(event) != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!service.isTrakGem(cursor)) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isTrakGem(target)) {
            return; // no target, or gem-onto-gem (meaningless)
        }
        event.setCancelled(true);
        TrakResult result = service.applyTo(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(target);
            player.updateInventory();
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
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
