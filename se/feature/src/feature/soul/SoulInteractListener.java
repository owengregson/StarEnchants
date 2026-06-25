package feature.soul;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click a held soul gem to toggle soul mode (§D); the primary affordance, {@code /se soulmode} is an
 * alias. Main-hand only so a two-hand interact does not double-toggle. Folia-correct: reads only the
 * player's own held item on their region thread.
 */
public final class SoulInteractListener implements Listener {

    private final SoulService souls;

    public SoulInteractListener(SoulService souls) {
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main-hand only: the off-hand pass would double-toggle
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack used = event.getItem();
        if (used == null || !souls.isGem(used)) {
            return;
        }
        event.setCancelled(true); // claim the gesture: the gem does nothing else on right-click
        Player player = event.getPlayer();
        souls.toggle(player);
    }
}
