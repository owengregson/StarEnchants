package feature.guard;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * Legacy (1.8.9) vanilla-station guard (G06): the anvil is the only guarded station that exists on 1.8 —
 * grindstones (1.14) and the smithing table (1.16) do not, and {@code PrepareAnvilEvent} is 1.9+ — so the 1.8
 * twin cancels the result-slot take via the floor-safe {@link InventoryClickEvent} when {@link StationGuardRules}
 * rejects the two anvil inputs. Every take path (normal, shift, number-key, drop-key, double-click-collect)
 * fires a click on raw slot 2, so a single cancel there denies the whole operation.
 *
 * <p>LOW mirrors the sibling {@code VanillaGuardListener}; the legacy nametag flow binds
 * {@code AnvilRename.UNSUPPORTED} (chat capture, no real anvil GUI), so there is no interplay to preserve.
 */
public final class LegacyAnvilGuard implements Listener {

    private final StationGuardRules rules;

    public LegacyAnvilGuard(StationGuardRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() != InventoryType.ANVIL || event.getRawSlot() != 2) {
            return;
        }
        if (rules.blockAnvil(top.getItem(0), top.getItem(1))) {
            event.setCancelled(true);
        }
    }
}
