package feature.scroll;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.lang.Messages;
import platform.sched.Scheduling;

/**
 * Holy white scroll glue (§I); logic lives in {@link HolyScrollService}. The apply gesture is a thin leaf of
 * the shared {@link ApplyGestureListener} (ADR-0041); the death/respawn hooks are Folia-correct (each event
 * fires on the affected player's own region thread):
 *
 * <ul>
 *   <li><b>apply</b> — drag the scroll onto gear to stamp its one-shot keep-on-death marker;</li>
 *   <li><b>death</b> — pull every holy-protected item out of the drops and stash it (priority {@code HIGH},
 *       after the {@code KEEP_ON_DEATH} enchant's {@code NORMAL}: a whole-inventory keep makes this a no-op
 *       and never spends a scroll);</li>
 *   <li><b>respawn</b> — re-grant the stashed items.</li>
 * </ul>
 */
public final class HolyScrollListener extends ApplyGestureListener {

    private final HolyScrollService service;
    private final KeptItemsStore kept;

    public HolyScrollListener(HolyScrollService service, KeptItemsStore kept, Messages messages) {
        super(messages);
        this.service = Objects.requireNonNull(service, "service");
        this.kept = Objects.requireNonNull(kept, "kept");
    }

    @Override
    protected boolean claimsCursor(ItemStack cursor) {
        return service.isHolyScroll(cursor);
    }

    @Override
    protected GestureOutcome apply(Player player, ItemStack cursor, ItemStack target, int slot) {
        return service.applyTo(cursor, target);
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
        messages.sendText(player, service.keptMessage(saved.size()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> saved = kept.drain(player.getUniqueId());
        if (saved.isEmpty()) {
            return;
        }
        // One tick after respawn, on the player's own region thread, so the inventory is restored first.
        Scheduling.onEntityLater(player, 1L, () -> saved.forEach(stack -> Inventories.giveOrDrop(player, stack)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        kept.clear(event.getPlayer().getUniqueId());
    }
}
