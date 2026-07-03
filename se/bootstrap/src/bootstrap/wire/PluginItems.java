package bootstrap.wire;

import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/** The plugin-item predicate defaults for a {@link FeatureModule} — {@link #NONE} contributes nothing. */
final class PluginItems {

    /** A module that guards no vanilla use: never claims a stack. The fold ORs these, so a NONE is a no-op. */
    static final Predicate<ItemStack> NONE = stack -> false;

    private PluginItems() {
    }
}
