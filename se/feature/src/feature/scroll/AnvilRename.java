package feature.scroll;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The version edge for the item-nametag rename GUI (§I; ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4):
 * {@code AnvilInventory.getRenameText()} and {@code PrepareAnvilEvent} arrived at 1.11/1.17 and are absent on
 * 1.8.9, so a real live anvil-rename field is a seam. This is a DEFAULT+OVR seam: the shared default
 * {@link #UNSUPPORTED} reports {@code supported()==false} (the listener falls back to chat capture), and the
 * modern override {@code ModernAnvilRename} ({@code overlay/modern}) opens a real server-side anvil and paints
 * a coloured result preview. Injected into {@code NametagListener} + the composition root.
 */
public interface AnvilRename {

    /** The anvil result slot (rawSlot in the view's top inventory). */
    int RESULT_SLOT = 2;

    /** Whether the raw-Bukkit anvil rename GUI is available on this server (false → the listener uses chat capture). */
    boolean supported();

    /** Open a real anvil for {@code player} with {@code input} in the first slot so the rename field is live. */
    void open(Player player, String title, ItemStack input);

    /** Whether {@code view}'s top inventory is an anvil. */
    boolean isAnvil(InventoryView view);

    /** The current rename-field text of {@code view}'s anvil, or {@code null} if absent/empty/not an anvil. */
    String renameText(InventoryView view);

    /** Register the coloured result-preview listener (modern only; no-op where unsupported). */
    void installPreview(Plugin plugin, NametagService service);

    /** 1.8.9 (and any era without the live anvil-rename field): unsupported — the listener uses chat capture. */
    AnvilRename UNSUPPORTED = new AnvilRename() {
        @Override
        public boolean supported() {
            return false;
        }

        @Override
        public void open(Player player, String title, ItemStack input) {
        }

        @Override
        public boolean isAnvil(InventoryView view) {
            return false;
        }

        @Override
        public String renameText(InventoryView view) {
            return null;
        }

        @Override
        public void installPreview(Plugin plugin, NametagService service) {
        }
    };
}
