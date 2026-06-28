package item.codec;

/**
 * The plugin's logical key names for on-item state (§4.2). Single key authority: these strings must
 * never drift, or items written under the old name stop resolving. They are <em>logical</em> names —
 * the {@link ItemBlobStore}/{@link ItemFlagStore} seam maps each to the platform's native key form
 * (a {@code starenchants:}-namespaced PDC key on modern; a raw NMS tag name on the 1.8 legacy fork),
 * so the codecs and this holder stay free of any version-specific key type (PDC {@code NamespacedKey}
 * does not exist on 1.8.9 — docs/legacy-1.8.9-codeshare-design.md §3.1).
 */
public final class ItemKeys {

    private final String combat;
    private final String soul;
    private final String carrier;
    private final String guarded;
    private final String crystalItem;
    private final String crystalExtractor;
    private final String heroicUpgrade;
    private final String slotItem;
    private final String slotSuccess;
    private final String scroll;
    private final String scrollConvert;
    private final String unopened;
    private final String godlyTransmog;
    private final String appliedSlot;
    private final String trakGem;
    private final String trakBlocks;
    private final String trakMobs;
    private final String trakSouls;

    private ItemKeys(String combat, String soul, String carrier, String guarded,
                     String crystalItem, String crystalExtractor, String heroicUpgrade,
                     String slotItem, String slotSuccess, String scroll, String scrollConvert,
                     String unopened, String godlyTransmog, String appliedSlot,
                     String trakGem, String trakBlocks, String trakMobs, String trakSouls) {
        this.combat = combat;
        this.soul = soul;
        this.carrier = carrier;
        this.guarded = guarded;
        this.crystalItem = crystalItem;
        this.crystalExtractor = crystalExtractor;
        this.heroicUpgrade = heroicUpgrade;
        this.slotItem = slotItem;
        this.slotSuccess = slotSuccess;
        this.scroll = scroll;
        this.scrollConvert = scrollConvert;
        this.unopened = unopened;
        this.godlyTransmog = godlyTransmog;
        this.appliedSlot = appliedSlot;
        this.trakGem = trakGem;
        this.trakBlocks = trakBlocks;
        this.trakMobs = trakMobs;
        this.trakSouls = trakSouls;
    }

    public static ItemKeys of() {
        return new ItemKeys("combat", "soul", "carrier", "guarded", "crystalitem", "crystalextractor",
                "heroicupgrade", "slotitem", "slotsuccess", "scroll", "scrollconvert", "unopened",
                "godlytransmog", "appliedslot", "trakgem", "trakblocks", "trakmobs", "traksouls");
    }

    public String combat() {
        return combat;
    }

    /** Separate from {@link #combat()}: souls change every spend/gain, which would thrash the content-hash cache (§5.2). */
    public String soul() {
        return soul;
    }

    /** Carrier (book/scroll/dust/gem) — separate from {@link #combat()} so it never decodes on the hot path (ADR-0016). */
    public String carrier() {
        return carrier;
    }

    /** Flags gear as guard-scroll protected; consumed on a failed apply to spare the item (white-scroll economy). */
    public String guarded() {
        return guarded;
    }

    public String crystalItem() {
        return crystalItem;
    }

    public String crystalExtractor() {
        return crystalExtractor;
    }

    public String heroicUpgrade() {
        return heroicUpgrade;
    }

    /** Slot-expander orb (§H); the granted slots persist in the gear's combat-blob {@code added} field, not here. */
    public String slotItem() {
        return slotItem;
    }

    /** The slot orb's per-item rolled success chance (§H); paired with {@link #slotItem()} on the orb. */
    public String slotSuccess() {
        return slotSuccess;
    }

    public String scroll() {
        return scroll;
    }

    /** The black scroll's rolled new-book conversion success rate (§I); paired with {@link #scroll()} on the scroll. */
    public String scrollConvert() {
        return scrollConvert;
    }

    public String unopened() {
        return unopened;
    }

    public String godlyTransmog() {
        return godlyTransmog;
    }

    /**
     * The single exclusive APPLIED-UTILITY slot (§I): an item may carry at most one of {white scroll, holy
     * white scroll, blocktrak, mobtrak, soultrak} at a time. Stores the occupant's kind; see {@link AppliedSlot}.
     */
    public String appliedSlot() {
        return appliedSlot;
    }

    /** Marks an UNAPPLIED trak gem + its kind (§I); distinct from the per-item lifetime counters below. */
    public String trakGem() {
        return trakGem;
    }

    /** Per-item lifetime blocks-broken counter (§I) — tracked in the background, separate from the combat blob. */
    public String trakBlocks() {
        return trakBlocks;
    }

    /** Per-item lifetime mobs-killed counter (§I). */
    public String trakMobs() {
        return trakMobs;
    }

    /** Per-item lifetime players-killed counter (§I). */
    public String trakSouls() {
        return trakSouls;
    }
}
