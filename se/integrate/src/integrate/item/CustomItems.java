package integrate.item;

import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Custom-item resolution for ItemsAdder and Oraxen (docs/decisions/0027): a {@code token → ItemStack} the
 * mint consults so a config can use a custom-textured item anywhere a material is accepted. Prefix-routed
 * ({@code itemsadder:…} / {@code oraxen:…}); any other token returns {@code null} so the caller falls back to
 * vanilla material resolution.
 */
public final class CustomItems {

    private static final String ITEMSADDER_PREFIX = "itemsadder:";
    private static final String ORAXEN_PREFIX = "oraxen:";

    private CustomItems() {
    }

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
            return null;
        };
    }

    private static boolean present(Plugin plugin, String name) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(name);
        return found != null && found.isEnabled();
    }
}
