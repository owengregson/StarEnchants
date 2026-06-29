package feature.scroll;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Legacy (1.8.9) stub of the anvil-rename seam — {@code AnvilInventory.getRenameText()} does not exist on 1.8
 * (added 1.11), so the item nametag falls back to chat capture. {@link #supported()} is {@code false}, so the
 * GUI methods are never invoked on the legacy fork. Same-FQN counterpart to {@code overlay/modern}.
 */
public final class NametagAnvil {

    public static final int RESULT_SLOT = 2;

    private NametagAnvil() {
    }

    public static boolean supported() {
        return false;
    }

    public static void open(Player player, String title, ItemStack input) {
        // unsupported on 1.8 — the listener uses chat capture when supported() is false
    }

    public static boolean isAnvil(InventoryView view) {
        return false;
    }

    public static String renameText(InventoryView view) {
        return null;
    }

    /** No-op on 1.8.9 — {@code PrepareAnvilEvent} does not exist, and chat capture has no result preview. */
    public static void installPreview(Plugin plugin, NametagService service) {
        // unsupported on 1.8 — the listener uses chat capture when supported() is false
    }
}
