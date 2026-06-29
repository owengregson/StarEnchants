package feature.scroll;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;

/**
 * Modern (1.17.1+) anvil-rename seam for the item nametag (§I): opens a REAL server-side anvil so its rename
 * field is live. Same-FQN counterpart to the {@code overlay/legacy} stub — {@code AnvilInventory.getRenameText()}
 * was added in 1.11, so the 1.8.9 fork reports unsupported and the listener falls back to chat capture.
 *
 * <p>A {@code createInventory(ANVIL)} inventory is NOT backed by a real anvil container, so {@code getRenameText()}
 * always returns null (the old bug — the confirm read nothing and the dialog re-rendered). {@link Player#openAnvil}
 * opens a real {@code AnvilMenu} instead. The display item is a CLONE; the listener clears the anvil before the
 * close so vanilla's input-return (which fires AFTER InventoryCloseEvent) never duplicates it. The result-slot
 * click is cancelled by the listener before vanilla's XP-affordability check, so the rename works at any level.
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

    /**
     * Open a real anvil for {@code player} with {@code input} in the first slot (so the rename field is live and
     * pre-fills the name). {@code force=true} opens it without an anvil block; the title is the vanilla "Repair
     * &amp; Name" ({@link Player#openAnvil} takes no custom title — the rename field is what matters here).
     */
    public static void open(Player player, String title, ItemStack input) {
        InventoryView view = player.openAnvil(player.getLocation(), true);
        if (view != null) {
            view.getTopInventory().setItem(0, input);
        }
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

    /**
     * Register the coloured result-preview listener (modern only; the legacy stub is a no-op). On a real anvil,
     * vanilla shows the typed name in the result slot as PLAIN text; this re-paints the result with the
     * {@code &}-translated name so the player previews the final colours. {@link PrepareAnvilEvent} does not
     * exist on 1.8.9, which is why this lives in the overlay and not the shared listener.
     */
    public static void installPreview(Plugin plugin, NametagService service) {
        plugin.getServer().getPluginManager().registerEvents(new PreviewListener(service), plugin);
    }

    private static final class PreviewListener implements Listener {

        private final NametagService service;

        PreviewListener(NametagService service) {
            this.service = service;
        }

        @EventHandler
        @SuppressWarnings("deprecation") // setDisplayName(String): the floor-stable item-meta path
        public void onPrepareAnvil(PrepareAnvilEvent event) {
            if (!(event.getView().getPlayer() instanceof Player player) || !service.inAnvil(player.getUniqueId())) {
                return;
            }
            AnvilInventory anvil = event.getInventory();
            ItemStack input = anvil.getItem(0);
            if (input == null || input.getType() == Material.AIR) {
                return;
            }
            String text = anvil.getRenameText();
            if (text == null || text.isEmpty()) {
                event.setResult(null); // no name typed yet — no result to show
                return;
            }
            ItemStack preview = input.clone();
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                // &-codes parsed AND the §I enchant-count suffix re-appended, so the preview matches the commit.
                meta.setDisplayName(service.previewName(input, text));
                preview.setItemMeta(meta);
            }
            event.setResult(preview);
            Scheduling.onEntity(player, player::updateInventory); // nudge the client to repaint the slot
        }
    }
}
