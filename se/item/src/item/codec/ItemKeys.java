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

    /** Build the key set under {@code plugin}'s namespace. */
    public static ItemKeys of(Plugin plugin) {
        return new ItemKeys(new NamespacedKey(plugin, "combat"), new NamespacedKey(plugin, "soul"),
                new NamespacedKey(plugin, "carrier"), new NamespacedKey(plugin, "guarded"),
                new NamespacedKey(plugin, "crystalitem"), new NamespacedKey(plugin, "crystalextractor"),
                new NamespacedKey(plugin, "heroicupgrade"),
                new NamespacedKey(plugin, "slotitem"), new NamespacedKey(plugin, "scroll"),
                new NamespacedKey(plugin, "unopened"), new NamespacedKey(plugin, "godlytransmog"));
    }

    /** The single key the combat-state blob is stored under (§5.1). */
    public NamespacedKey combat() {
        return combat;
    }

    /**
     * The key the soul-gem state lives under — DELIBERATELY separate from {@link #combat()}: souls
     * change on every spend/gain, and folding them into the combat blob would thrash the content-hash
     * {@code ItemView} cache and force a lore re-render on each hit (§5.1, §5.2).
     */
    public NamespacedKey soul() {
        return soul;
    }

    /**
     * The key that marks an item as an identity/economy CARRIER (a book/scroll/dust/gem that applies an
     * enchant to OTHER gear) — separate from {@link #combat()} so a carrier never decodes on the combat
     * hot path (ADR-0016; item-data-model "two records: combat vs identity").
     */
    public NamespacedKey carrier() {
        return carrier;
    }

    /**
     * The key that flags GEAR as protected by a guard scroll — consumed on a failed carrier apply to
     * spare the item from destruction (the white-scroll economy). Separate from the combat blob.
     */
    public NamespacedKey guarded() {
        return guarded;
    }

    /**
     * The key the physical CRYSTAL item's component keys live under (docs/v3-directives.md §E) — a
     * crystal is its own item (single or merged multi-crystal), distinct from the {@link #carrier()}
     * economy and never on the combat hot path.
     */
    public NamespacedKey crystalItem() {
        return crystalItem;
    }

    /**
     * The key that marks an item as a crystal EXTRACTOR (docs/v3-directives.md §E) — a one-shot consumable
     * dragged onto crystal-bearing gear to pop its last crystal back into a whole crystal item. An identity
     * marker, off the combat hot path.
     */
    public NamespacedKey crystalExtractor() {
        return crystalExtractor;
    }

    /**
     * The key that marks an item as a HEROIC UPGRADE (docs/v3-directives.md §F) — a one-shot consumable
     * dragged onto armour/weapon to attempt a heroic upgrade. An identity marker, off the combat hot path.
     */
    public NamespacedKey heroicUpgrade() {
        return heroicUpgrade;
    }

    /**
     * The key that marks an item as a SLOT EXPANDER orb (docs/v3-directives.md §H) — a one-shot
     * consumable dragged onto gear to raise its enchant-slot count, storing the {@code +N} it grants.
     * An identity marker, off the combat hot path (the granted slots persist in the combat blob's
     * {@code added} field, not here).
     */
    public NamespacedKey slotItem() {
        return slotItem;
    }

    /**
     * The key that tags a SCROLL by kind (docs/v3-directives.md §I) — a string marker shared by the
     * scroll family (black / randomizer / and later transmog / holy / nametag). An identity marker, off
     * the combat hot path; the scroll's mechanics come from the scrolls config, not the item.
     */
    public NamespacedKey scroll() {
        return scroll;
    }

    /**
     * The key that marks an UNOPENED / RANDOMIZED book (docs/v3-directives.md §I) and stores the tier it
     * is scoped to — right-clicking it yields a concrete enchant book of a random enchant from that tier.
     * An identity marker, off the combat hot path.
     */
    public NamespacedKey unopened() {
        return unopened;
    }

    /**
     * The key that marks an item as a physical GODLY TRANSMOG tool (docs/v3-directives.md §I/§K) — dragged
     * onto enchanted gear, it opens the deterministic enchant-reorder GUI bound to that piece (it is NOT a
     * one-shot scroll, so it lives off the {@link #scroll()} consume path). An identity marker, off the hot path.
     */
    public NamespacedKey godlyTransmog() {
        return godlyTransmog;
    }
}
