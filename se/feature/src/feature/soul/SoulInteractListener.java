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
 * Right-click a held soul gem to toggle soul mode (docs/v3-directives.md §D) — the gem is a DISTINCT
 * item, and the gesture is its primary affordance ({@code /se soulmode} stays as an alias). Fires on
 * the player's own region thread (Folia-correct: reads only the player's own held item), and ONLY for
 * the main hand so a two-hand interact does not double-toggle. When the used item is a gem the event is
 * cancelled, claiming the gesture so the right-click does nothing else.
 */
public final class SoulInteractListener implements Listener {

    private final SoulService souls;

    public SoulInteractListener(SoulService souls) {
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main-hand only — the off-hand pass of a two-hand interact would double-toggle
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack used = event.getItem();
        if (used == null || !souls.isGem(used)) {
            return; // not a soul gem — leave the interaction alone
        }
        event.setCancelled(true); // claim the gesture: the gem does nothing else on right-click
        Player player = event.getPlayer();
        souls.toggle(player); // on the player's own thread; toggle reads the main-hand gem + seeds the ledger
    }
}
