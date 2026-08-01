package feature.scroll;

import feature.apply.ApplyGestureListener;
import feature.apply.GestureOutcome;
import feature.compat.Sounds;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
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
 *   <li><b>death</b> {@code @HIGH} — pull every holy-protected item out of the drops (after the
 *       {@code KEEP_ON_DEATH} enchant's {@code NORMAL}: a whole-inventory keep makes this a no-op and never
 *       spends a scroll) and park it, pending the final keep decision;</li>
 *   <li><b>death</b> {@code @MONITOR} — commit the stash + message ONLY if keepInventory is still false. A
 *       later handler flipping it TRUE (a grave / region / keep plugin at HIGHEST/MONITOR) means vanilla keeps
 *       the whole live inventory — which still holds the protected originals — so stashing would re-grant a
 *       duplicate one tick after respawn (F12); in that case we discard the parked copies instead;</li>
 *   <li><b>respawn</b> — re-grant the committed stash.</li>
 * </ul>
 *
 * <p>The residual window is a MONITOR flipper registered AFTER us — the best Bukkit ordering allows (there is
 * no priority past MONITOR). Both death handlers run on the dying player's own region thread.
 */
public final class HolyScrollListener extends ApplyGestureListener {

    private final HolyScrollService service;
    private final KeptItemsStore kept;

    /** Per-death holy items pulled at HIGH, awaiting the final keep decision at MONITOR; always drained there. */
    private final Map<UUID, List<ItemStack>> pending = new ConcurrentHashMap<>();

    public HolyScrollListener(HolyScrollService service, KeptItemsStore kept, Messages messages, Sounds sounds) {
        super(messages, sounds);
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
        // Pull holy items out of the drops here (they must not spawn), but hold the stash until the keep decision
        // is final at MONITOR — see onDeathCommit.
        List<ItemStack> saved = service.keepFromDrops(event.getEntity(), event.getDrops());
        if (!saved.isEmpty()) {
            pending.put(event.getEntity().getUniqueId(), saved);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathCommit(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> saved = pending.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        if (event.getKeepInventory()) {
            return; // flipped true after HIGH: the live inventory kept the originals — stashing would dupe (F12)
        }
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
}
