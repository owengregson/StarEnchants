package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * The plugin's versioned {@link NamespacedKey}s for on-item PDC state (docs/architecture.md §4.2).
 * Keys are owned in one place so the namespace is consistent for the life of an item — the
 * stable string under which the {@link CombatCodec} blob lives must never drift, or an old item
 * stops resolving. Built once from the plugin at boot; the item module never invents a namespace
 * itself (it stays Plugin-free except here, the single key authority).
 */
public final class ItemKeys {

    private final NamespacedKey combat;

    private ItemKeys(NamespacedKey combat) {
        this.combat = combat;
    }

    /** Build the key set under {@code plugin}'s namespace. */
    public static ItemKeys of(Plugin plugin) {
        return new ItemKeys(new NamespacedKey(plugin, "combat"));
    }

    /** The single key the combat-state blob is stored under (§5.1). */
    public NamespacedKey combat() {
        return combat;
    }
}
