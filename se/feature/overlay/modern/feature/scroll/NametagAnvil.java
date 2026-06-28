package feature.scroll;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Modern (1.17.1+) anvil-rename seam for the item nametag (§I): opens a real anvil GUI and reads its rename
 * field. Same-FQN counterpart to the {@code overlay/legacy} stub — {@code AnvilInventory.getRenameText()} was
 * added in 1.11, so the 1.8.9 fork reports unsupported and the listener falls back to chat capture.
 *
 * <p>Pure raw Bukkit (no shaded AnvilGUI dependency). The result-slot click is cancelled by the listener
 * before vanilla's XP-affordability check runs, so the rename works regardless of the player's level.
 */
public final class NametagAnvil {

    /** The anvil result slot (rawSlot in the view's top inventory). */
    public static final int RESULT_SLOT = 2;

    private NametagAnvil() {
    }

    /** Whether the raw-Bukkit anvil rename GUI is available on this server (always on modern). */
    public static boolean supported() {
        return true;
    }

    /** Open an anvil GUI titled {@code title} with {@code input} in the first slot (so the rename field is live). */
    @SuppressWarnings("deprecation") // createInventory(..., String): the floor-stable title overload (Component is Adventure-only)
    public static void open(Player player, String title, ItemStack input) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.ANVIL, title);
        inv.setItem(0, input);
        player.openInventory(inv);
    }

    /** Whether {@code view}'s top inventory is an anvil. */
    public static boolean isAnvil(InventoryView view) {
        return view != null && view.getTopInventory() instanceof AnvilInventory;
    }

    /** The current rename-field text of {@code view}'s anvil, or {@code null} if absent/empty/not an anvil. */
    public static String renameText(InventoryView view) {
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
            return null;
        }
        String text = anvil.getRenameText();
        return text == null || text.isEmpty() ? null : text;
    }
}
