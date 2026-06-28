package feature.scroll;

import feature.compat.MenuClicks;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * Holy white scroll glue (§I); logic lives in {@link HolyScrollService}. Three hooks, all Folia-correct (each
 * event fires on the affected player's own region thread):
 *
 * <ul>
 *   <li><b>apply</b> — drag the scroll onto gear to stamp its one-shot keep-on-death marker;</li>
 *   <li><b>death</b> — pull every holy-protected item out of the drops and stash it (priority {@code HIGH},
 *       after the {@code KEEP_ON_DEATH} enchant's {@code NORMAL}: a whole-inventory keep makes this a no-op
 *       and never spends a scroll);</li>
 *   <li><b>respawn</b> — re-grant the stashed items.</li>
 * </ul>
 */
public final class HolyScrollListener implements Listener {

    private final HolyScrollService service;
    private final KeptItemsStore kept;

    public HolyScrollListener(HolyScrollService service, KeptItemsStore kept) {
        this.service = Objects.requireNonNull(service, "service");
        this.kept = Objects.requireNonNull(kept, "kept");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
            return;
        }
        if (MenuClicks.clickedInventory(event) == null
                || MenuClicks.clickedInventory(event) != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!service.isHolyScroll(cursor)) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isHolyScroll(target)) {
            return; // scroll-onto-scroll / empty slot is meaningless
        }
        event.setCancelled(true);
        ScrollResult result = service.applyTo(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget());
            player.updateInventory();
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return; // the world (gamerule / enchant) already keeps everything — no scroll needed or spent
        }
        List<ItemStack> saved = service.keepFromDrops(event.getDrops());
        if (saved.isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        kept.stash(player.getUniqueId(), saved);
        player.sendMessage(service.keptMessage(saved.size()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> saved = kept.drain(player.getUniqueId());
        if (saved.isEmpty()) {
            return;
        }
        // One tick after respawn, on the player's own region thread, so the inventory is restored first.
        Scheduling.onEntityLater(player, 1L, () -> saved.forEach(stack ->
                player.getInventory().addItem(stack).values()
                        .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        kept.clear(event.getPlayer().getUniqueId());
    }
}
