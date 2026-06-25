package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * The plugin's versioned {@link NamespacedKey}s for on-item PDC state (§4.2). Single key authority:
 * these strings must never drift, or items written under the old namespace stop resolving. Built once
 * at boot; the rest of the item module stays Plugin-free.
 */
public final class ItemKeys {

    private final NamespacedKey combat;
    private final NamespacedKey soul;
    private final NamespacedKey carrier;
    private final NamespacedKey guarded;
    private final NamespacedKey crystalItem;
    private final NamespacedKey crystalExtractor;
    private final NamespacedKey heroicUpgrade;
    private final NamespacedKey slotItem;
    private final NamespacedKey scroll;
    private final NamespacedKey unopened;
    private final NamespacedKey godlyTransmog;

    private ItemKeys(NamespacedKey combat, NamespacedKey soul, NamespacedKey carrier, NamespacedKey guarded,
                     NamespacedKey crystalItem, NamespacedKey crystalExtractor, NamespacedKey heroicUpgrade,
                     NamespacedKey slotItem, NamespacedKey scroll, NamespacedKey unopened,
                     NamespacedKey godlyTransmog) {
        this.combat = combat;
        this.soul = soul;
        this.carrier = carrier;
        this.guarded = guarded;
        this.crystalItem = crystalItem;
        this.crystalExtractor = crystalExtractor;
        this.heroicUpgrade = heroicUpgrade;
        this.slotItem = slotItem;
        this.scroll = scroll;
        this.unopened = unopened;
        this.godlyTransmog = godlyTransmog;
    }

    public static ItemKeys of(Plugin plugin) {
        return new ItemKeys(new NamespacedKey(plugin, "combat"), new NamespacedKey(plugin, "soul"),
                new NamespacedKey(plugin, "carrier"), new NamespacedKey(plugin, "guarded"),
                new NamespacedKey(plugin, "crystalitem"), new NamespacedKey(plugin, "crystalextractor"),
                new NamespacedKey(plugin, "heroicupgrade"),
                new NamespacedKey(plugin, "slotitem"), new NamespacedKey(plugin, "scroll"),
                new NamespacedKey(plugin, "unopened"), new NamespacedKey(plugin, "godlytransmog"));
    }

    public NamespacedKey combat() {
        return combat;
    }

    /** Separate from {@link #combat()}: souls change every spend/gain, which would thrash the content-hash cache (§5.2). */
    public NamespacedKey soul() {
        return soul;
    }

    /** Carrier (book/scroll/dust/gem) — separate from {@link #combat()} so it never decodes on the hot path (ADR-0016). */
    public NamespacedKey carrier() {
        return carrier;
    }

    /** Flags gear as guard-scroll protected; consumed on a failed apply to spare the item (white-scroll economy). */
    public NamespacedKey guarded() {
        return guarded;
    }

    public NamespacedKey crystalItem() {
        return crystalItem;
    }

    public NamespacedKey crystalExtractor() {
        return crystalExtractor;
    }

    public NamespacedKey heroicUpgrade() {
        return heroicUpgrade;
    }

    /** Slot-expander orb (§H); the granted slots persist in the gear's combat-blob {@code added} field, not here. */
    public NamespacedKey slotItem() {
        return slotItem;
    }

    public NamespacedKey scroll() {
        return scroll;
    }

    public NamespacedKey unopened() {
        return unopened;
    }

    public NamespacedKey godlyTransmog() {
        return godlyTransmog;
    }
}
