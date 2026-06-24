package integrate.item;

import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Custom-item resolution for ItemsAdder and Oraxen (docs/decisions/0027): a {@code token → ItemStack}
 * function StarEnchants consults when minting an item likeness, so a config can use a custom-textured item
 * (e.g. {@code material: itemsadder:ruby_scroll} or {@code material: oraxen:amethyst_dust}) anywhere a
 * material is accepted. Installed as {@code item.mint.ItemFactory.customItemResolver}.
 *
 * <p>Prefix-routed: {@code itemsadder:<namespace:id>} → ItemsAdder, {@code oraxen:<id>} → Oraxen; any other
 * token (a vanilla material, etc.) returns {@code null} so the caller falls back to vanilla material
 * resolution. Each backend is only consulted when its plugin is present + enabled, and each bridge is
 * fail-safe, so this never throws on a mint and never loads an absent plugin's API.
 */
public final class CustomItems {

    private static final String ITEMSADDER_PREFIX = "itemsadder:";
    private static final String ORAXEN_PREFIX = "oraxen:";

    private CustomItems() {
    }

    /** The composed custom-item resolver, or a constant {@code null} resolver when neither plugin is active. */
    public static Function<String, ItemStack> resolver(Plugin plugin, Predicate<String> enabled) {
        boolean itemsAdder = enabled.test("itemsadder") && present(plugin, "ItemsAdder");
        boolean oraxen = enabled.test("oraxen") && present(plugin, "Oraxen");
        if (!itemsAdder && !oraxen) {
            return token -> null;
        }
        return token -> {
            if (token == null) {
                return null;
            }
            if (itemsAdder && token.regionMatches(true, 0, ITEMSADDER_PREFIX, 0, ITEMSADDER_PREFIX.length())) {
                return ItemsAdder.resolve(token.substring(ITEMSADDER_PREFIX.length()));
            }
            if (oraxen && token.regionMatches(true, 0, ORAXEN_PREFIX, 0, ORAXEN_PREFIX.length())) {
                return Oraxen.resolve(token.substring(ORAXEN_PREFIX.length()));
            }
            return null; // not a custom-item token → caller resolves it as a vanilla material
        };
    }

    private static boolean present(Plugin plugin, String name) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(name);
        return found != null && found.isEnabled();
    }
}
