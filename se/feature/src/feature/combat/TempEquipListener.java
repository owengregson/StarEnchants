package feature.combat;

import engine.sink.TempEquip;
import java.util.ArrayList;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps a temporary {@code EQUIP_SWAP} (spooky's pumpkin helmet) from costing the victim their real gear. On
 * death ({@code @LOWEST}, before the scroll/keep listeners read) it puts the REAL piece back into the drops AND
 * the worn slot, so death behaves exactly as if no swap happened (the real helmet drops / a holy scroll keeps
 * it; the placeholder never drops). On quit it restores the worn slot so the saved inventory keeps the real
 * piece. The timed revert in the Sink covers the normal case; this covers the death/logout edges.
 */
public final class TempEquipListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<Integer, ItemStack> active = TempEquip.active(player.getUniqueId());
        if (active.isEmpty()) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (Integer slot : new ArrayList<>(active.keySet())) {
            ItemStack original = TempEquip.end(player.getUniqueId(), slot);
            if (slot < 0 || slot >= armor.length) {
                continue;
            }
            ItemStack placeholder = armor[slot];
            if (placeholder != null) {
                event.getDrops().remove(placeholder); // the placeholder never drops
            }
            ItemStack real = TempEquip.isAir(original) ? null : original;
            if (real != null) {
                event.getDrops().add(real); // the real piece drops as usual (scroll/keep listeners see it)
            }
            armor[slot] = real; // and is what a keepInventory player keeps
        }
        player.getInventory().setArmorContents(armor);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Map<Integer, ItemStack> active = TempEquip.active(player.getUniqueId());
        if (active.isEmpty()) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (Integer slot : new ArrayList<>(active.keySet())) {
            ItemStack original = TempEquip.end(player.getUniqueId(), slot);
            if (slot >= 0 && slot < armor.length) {
                armor[slot] = TempEquip.isAir(original) ? null : original; // saved inv keeps the real piece
            }
        }
        player.getInventory().setArmorContents(armor);
    }
}
