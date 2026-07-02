package feature.soul;

import feature.compat.Hands;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click a held soul gem to toggle soul mode (§D); {@code /se soulmode} is an alias. Main-hand only so
 * a two-hand interact does not double-toggle.
 */
public final class SoulInteractListener implements Listener {

    private final SoulService souls;
    private final Hands hands;

    public SoulInteractListener(SoulService souls, Hands hands) {
        this.souls = Objects.requireNonNull(souls, "souls");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    // priority LOW, NOT ignoreCancelled: a RIGHT_CLICK_BLOCK with a non-use item (the gem) often arrives
    // already cancelled (vanilla deny / a protection plugin), so ignoreCancelled would silently drop the
    // FIRST toggle until /se soulmode primed the mode. LOW still runs before TriggerListeners (HIGH), so the
    // gem claims the gesture ahead of INTERACT enchant triggers.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event)) {
            return; // main-hand only: the off-hand pass would double-toggle
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        // Read the gem from the main hand directly (the hand toggle() also re-reads), not event.getItem(),
        // which can be null on a RIGHT_CLICK_AIR with no usable item.
        ItemStack used = hands.mainHand(player);
        if (used == null || !souls.isGem(used)) {
            return;
        }
        event.setCancelled(true); // claim the gesture: the gem does nothing else on right-click
        souls.toggle(player);
    }
}
